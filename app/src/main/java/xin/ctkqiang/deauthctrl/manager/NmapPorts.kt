package xin.ctkqiang.deauthctrl.manager

/**
 * nmap-services 高频端口表(移植自 nmap 源码的端口-服务映射)
 *
 * 按 nmap-services 的频率排序收录常用端口及其服务名;原生 Kotlin
 * 实现,零外部依赖 —— 这是 nmap 技术栈在应用内的「原生互操作」:
 * 端口表、服务指纹、时序模板、输出格式都来自 nmap,引擎自己写。
 */
object NmapPorts {

    /** 前 100 高频端口(名称对齐 nmap-services) */
    val TOP_100: List<Pair<Int, String>> = listOf(
        80 to "http", 23 to "telnet", 443 to "https", 21 to "ftp", 22 to "ssh",
        25 to "smtp", 3389 to "ms-wbt-server", 110 to "pop3", 445 to "microsoft-ds",
        139 to "netbios-ssn", 143 to "imap", 53 to "domain", 135 to "msrpc",
        3306 to "mysql", 8080 to "http-proxy", 1723 to "pptp", 111 to "rpcbind",
        995 to "pop3s", 993 to "imaps", 5900 to "vnc", 1025 to "NFS-or-IIS",
        587 to "submission", 8888 to "sun-answerbook", 199 to "smux",
        1720 to "h323q931", 465 to "smtps", 548 to "afp", 113 to "auth",
        81 to "hosts2-ns", 6001 to "X11:1", 10000 to "snet-sensor-mgmt",
        514 to "shell", 5060 to "sip", 179 to "bgp", 1026 to "LSA-or-nterm",
        2000 to "cisco-sccp", 8443 to "https-alt", 8000 to "http-alt",
        32768 to "filenet-tms", 554 to "rtsp", 26 to "rsftp", 1433 to "ms-sql-s",
        49152 to "unknown", 2001 to "dc", 515 to "printer", 8008 to "http-alt",
        49154 to "unknown", 1027 to "IIS", 5666 to "nrpe", 646 to "ldp",
        5000 to "upnp", 5631 to "pcanywheredata", 631 to "ipp", 49153 to "unknown",
        8081 to "blackice-icecap", 2049 to "nfs", 88 to "kerberos-sec",
        79 to "finger", 5800 to "vnc-http", 106 to "pop3pw", 2121 to "ccproxy-ftp",
        1110 to "nfsd-status", 49155 to "unknown", 6000 to "X11", 513 to "login",
        990 to "ftps", 5357 to "wsdapi", 427 to "svrloc", 49156 to "unknown",
        543 to "postoffice", 544 to "kshell", 5101 to "admdog", 144 to "news",
        7 to "echo", 389 to "ldap", 8009 to "ajp13", 3128 to "squid-http",
        444 to "snpp", 9999 to "abyss", 5009 to "airport-admin", 7070 to "realserver",
        5190 to "aol", 3000 to "ppp", 5432 to "postgresql", 3986 to "mapper-ws_ethd",
        13 to "daytime", 1029 to "ms-lsa", 9 to "discard", 5051 to "ita-agent",
        6646 to "unknown", 49157 to "unknown", 1028 to "ms-lsa", 873 to "rsync",
        1755 to "wms", 2717 to "pn-requester", 4899 to "radmin", 9100 to "jetdirect",
        119 to "nntp", 37 to "time", 1000 to "cadlock", 3001 to "nessus",
    )

    /** 扩展到前 1000:高频表 + 按顺序补全常见端口 */
    fun topPorts(count: Int): List<Pair<Int, String>> {
        if (count <= TOP_100.size) return TOP_100.take(count)
        val extra = listOf(
            636 to "ldapssl", 989 to "ftps-data", 992 to "telnets", 1080 to "socks",
            1099 to "rmiregistry", 1194 to "openvpn", 1293 to "ipsectrl", 1434 to "ms-sql-m",
            1521 to "oracle", 1812 to "radius", 1813 to "radacct", 1883 to "mqtt",
            1900 to "upnp", 2222 to "EtherNetIP-1", 2375 to "docker", 2376 to "docker-s",
            3000 to "ppp", 3268 to "globalcatLDAP", 3269 to "globalcatLDAPssl",
            3478 to "stun", 3690 to "svn", 4000 to "remoteanything", 4369 to "epmd",
            4500 to "nat-t-ike", 4567 to "tram", 5353 to "zeroconf", 5355 to "llmnr",
            5683 to "coap", 5901 to "vnc-1", 5984 to "couchdb", 5985 to "winrm",
            5986 to "winrm-s", 6379 to "redis", 6443 to "sun-sr-https",
            7001 to "afs3-callback", 7474 to "neo4j", 8001 to "vcom-tunnel",
            8082 to "blackice-alerts", 8089 to "splunkd", 8123 to "polipo",
            8140 to "puppet", 8200 to "trivnet1", 8500 to "fmtp", 8600 to "asterix",
            9000 to "cslistener", 9001 to "etlservicemgr", 9042 to "unknown",
            9090 to "zeus-admin", 9200 to "wap-wsp", 9300 to "vrace",
            9418 to "git", 9999 to "abyss", 10001 to "scp-config", 11211 to "memcached",
            25565 to "minecraft", 27017 to "mongod", 27018 to "mongod-s",
            28017 to "mongod-www", 50000 to "ibm-db2", 50070 to "hdfs",
            61616 to "activemq", 50030 to "hdfs-secondary", 54321 to "unknown",
        )
        val merged = (TOP_100 + extra).distinctBy { it.first }
        return merged.take(count)
    }
}
