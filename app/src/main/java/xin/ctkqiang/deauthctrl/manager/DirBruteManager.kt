package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

data class BruteResult(val path: String, val status: Int, val size: Long, val timeMs: Long)

class DirBruteManager {
    @Volatile var isRunning = false; private set
    private var job: Job? = null

    private val wordlist = listOf(
        "admin", "login", "wp-admin", "wp-login.php", "administrator", "panel",
        "phpmyadmin", "dbadmin", "mysql", "backup", "backups", "old", "new", "test",
        "dev", "staging", "api", "api/v1", "api/v2", "graphql", "rest", "soap",
        ".git", ".git/config", ".env", ".env.bak", ".DS_Store", "robots.txt",
        "sitemap.xml", "crossdomain.xml", "phpinfo.php", "info.php", "test.php",
        "config.php", "config.php.bak", "wp-config.php", "wp-config.php.bak",
        "database.sql", "dump.sql", "db.sql", "backup.zip", "backup.tar.gz",
        "uploads", "upload", "images", "img", "css", "js", "static", "assets",
        "vendor", "node_modules", "composer.json", "package.json", "Dockerfile",
        "docker-compose.yml", ".htaccess", ".htpasswd", "web.config",
        "cgi-bin", "cgi-bin/", ".cgi", "shell.php", "cmd.php", "upload.php",
        "admin.php", "admin.asp", "admin.aspx", "admin.jsp", "manager/html",
        "server-status", "server-info", "actuator", "actuator/health", "swagger",
        "swagger-ui.html", "api-docs", "v2/api-docs", "console", "admin/console",
        "jmx-console", "web-console", "invoker/JMXInvokerServlet", "webdav",
        ".svn", ".svn/entries", ".hg", ".bzr", "CVS", ".gitignore",
        "WEB-INF", "WEB-INF/web.xml", "META-INF", "WEB-INF/classes",
        "debug", "debug/default.jsp", "trace.axd", "elmah.axd",
        "webmail", "mail", "roundcube", "squirrelmail", "horde",
        "cpanel", "whm", "plesk", "ispconfig", "webmin",
        "phpMyAdmin", "pma", "myadmin", "mysql-admin", "sql",
        "wp-content", "wp-includes", "wp-json", "wp-json/wp/v2/users",
        "xmlrpc.php", "wp-cron.php", "wp-trackback.php", ".well-known",
        "security.txt", ".well-known/security.txt", "acme-challenge",
        "owa", "ecp", "autodiscover", "ews", "oab",
    )

    fun start(baseUrl: String, onResult: (BruteResult) -> Unit, onProgress: (Int, Int) -> Unit, onComplete: () -> Unit) {
        stop()
        isRunning = true
        job = CoroutineScope(Dispatchers.IO).launch {
            val url = baseUrl.trimEnd('/')
            var done = 0
            val total = wordlist.size
            for (word in wordlist) {
                if (!isRunning) break
                val path = "/$word"
                val start = System.currentTimeMillis()
                try {
                    val conn = URL("$url$path").openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000; conn.readTimeout = 3000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn.instanceFollowRedirects = false
                    val status = conn.responseCode
                    val size = try { conn.contentLength.toLong() } catch (_: Exception) { 0L }
                    val timeMs = System.currentTimeMillis() - start
                    if (status !in listOf(404, 400, 502, 503)) {
                        withContext(Dispatchers.Main) { onResult(BruteResult(path, status, size, timeMs)) }
                    }
                    conn.disconnect()
                } catch (_: Exception) {}
                done++
                withContext(Dispatchers.Main) { onProgress(done, total) }
                delay(50)
            }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun stop() { isRunning = false; job?.cancel(); job = null }
}
