package csbase.fogbow;

import java.util.Map;
import java.util.Properties;

import csbase.server.plugin.service.sgaservice.ISGADataTransfer;
import csbase.server.plugin.service.sgaservice.SGADataTransferException;

public class SSHSGADataTransfer implements ISGADataTransfer {

	private String host;
	private int port;
	private String pubKey;

	public SSHSGADataTransfer(String host, int port, String pubKey) {
		this.host = host;
		this.port = port;
		this.pubKey = pubKey;
	}
	
	@Override
	public boolean checkExistence(String[] arg0)
			throws SGADataTransferException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void copyFrom(String[] arg0, String[] arg1)
			throws SGADataTransferException {
		// TODO Auto-generated method stub

	}

	@Override
	public void copyTo(String[] arg0, String[] arg1)
			throws SGADataTransferException {
		// TODO Auto-generated method stub

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
