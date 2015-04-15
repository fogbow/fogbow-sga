package csbase.fogbow;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import csbase.server.plugin.service.sgaservice.ISGADataTransfer;
import csbase.server.plugin.service.sgaservice.SGADataTransferException;

public class SSHSGADataTransfer implements ISGADataTransfer {

	private String host;
	private int port;
	private String privateKey;
	private String userName;

	public SSHSGADataTransfer(String host, int port, String userName, String privateKey) {
		this.host = host;
		this.port = port;
		this.userName = userName;
		this.privateKey = privateKey;
	}
	
	@Override
	public boolean checkExistence(String[] arg0)
			throws SGADataTransferException {
		return false;
	}

	@Override
	public void copyFrom(String[] arg0, String[] arg1)
			throws SGADataTransferException {
		SSHClientWrapper wrapper = new SSHClientWrapper();
		try {
			wrapper.connect(host, port, userName, privateKey);
			for (int i = 0; i < arg0.length; i++) {
				wrapper.doScpDownload(arg1[i], arg0[i]);
			}
		} catch (Exception e) {
			throw new SGADataTransferException(e);
		} finally {
			try {
				wrapper.disconnect();
			} catch (IOException e) {
			}
		}
	}

	@Override
	public void copyTo(String[] arg0, String[] arg1)
			throws SGADataTransferException {
		SSHClientWrapper wrapper = new SSHClientWrapper();
		try {
			wrapper.connect(host, port, userName, privateKey);
			for (int i = 0; i < arg0.length; i++) {
				wrapper.doScpUpload(arg0[i], arg1[i]);
			}
		} catch (Exception e) {
			throw new SGADataTransferException(e);
		} finally {
			try {
				wrapper.disconnect();
			} catch (IOException e) {
			}
		}
	}

	@Override
	public void createDirectory(String[] arg0) throws SGADataTransferException {
		// TODO Auto-generated method stub

	}

	@Override
	public String[] getAlgorithmsRootPath() throws SGADataTransferException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String[], Long> getLocalTimestamps(String[] arg0)
			throws SGADataTransferException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getProjectsRootPath() throws SGADataTransferException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String[], Long> getRemoteTimestamps(String[] arg0)
			throws SGADataTransferException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void remove(String[] arg0) throws SGADataTransferException {
		// TODO Auto-generated method stub

	}

	@Override
	public void setSGAProperties(Properties arg0) {
		// TODO Auto-generated method stub

	}

}
