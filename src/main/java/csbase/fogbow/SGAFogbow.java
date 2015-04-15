package csbase.fogbow;

import csbase.server.plugin.service.IServiceManager;
import csbase.sga.SGALocal;

public class SGAFogbow extends SGALocal {

	public SGAFogbow(IServiceManager serviceManager) {
		super(serviceManager);
		setExecutor(new FogbowExecutor());
		setMonitor(new FogbowMonitor());
	}

}
