package csbase.fogbow;

import java.io.File;
import java.io.IOException;
import java.security.PublicKey;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;

public class SSHClientWrapper {

    private SSHClient client;
    private Session session;

    public SSHClientWrapper() {
        this(new SSHClient());
    }

    public SSHClientWrapper(SSHClient ssh) {
        this.client = ssh;
        addBlankHostKeyVerifier(client);
    }

    private void addBlankHostKeyVerifier(SSHClient ssh) {
        ssh.addHostKeyVerifier(new HostKeyVerifier() {
            @Override
            public boolean verify(String arg0, int arg1, PublicKey arg2) {
                return true;
            }
        });
    }

    public SSHClient getSshClient() {
        return client;
    }

    public Integer doSshExecution(String command) throws IOException {
        session = client.startSession();
        Command cmd = session.exec(command);
        cmd.join();
        return cmd.getExitStatus();
    }

    public void disconnect() throws IOException {
        if (session != null) {
            session.close();
            session = null;
        }
        client.disconnect();
        client.close();
    }

    public void doScpDownload(String localFilePath, String remoteFilePath)
            throws IOException {
        FileSystemFile localFile = new FileSystemFile(localFilePath);
        client.newSCPFileTransfer().download(remoteFilePath, localFile);
    }

    public void doScpUpload(String localFilePath, String remoteFilePath)
            throws IOException {
    	doSshExecution("mkdir -p " + new File(remoteFilePath).getParent());
        FileSystemFile localFile = new FileSystemFile(localFilePath);
        SCPFileTransfer scp = client.newSCPFileTransfer();
        scp.upload(localFile, remoteFilePath);
    }

    public void connect(String host, int port, String userName,
            String privateKeyFilePath, int timeOut) throws IOException {
    	disconnect();
    	client = new SSHClient();
    	if (timeOut > 0) {
    		client.setConnectTimeout(timeOut);
    	}
    	addBlankHostKeyVerifier(client);
    	client.connect(host, port);
    	if (userName != null && privateKeyFilePath != null) {
    		client.authPublickey(userName, privateKeyFilePath);
    	}
    }
    
    public void connect(String address, int port, String userName,
            String privateKeyFilePath) throws IOException {
        connect(address, port, userName, privateKeyFilePath, 0);
    }
    
    public void connect(String address, int port) throws IOException {
    	connect(address, port, null, null, 0);
    }
}
