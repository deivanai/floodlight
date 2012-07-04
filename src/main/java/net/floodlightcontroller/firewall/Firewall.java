package net.floodlightcontroller.firewall;

import java.util.Collection;
import java.util.Map;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import net.floodlightcontroller.core.IFloodlightProviderService;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.Set;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.IRoutingService;

import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Firewall implements IOFMessageListener, IFloodlightModule {

	protected IFloodlightProviderService floodlightProvider;
	protected IRoutingService routingEngine;
	protected Set macAddresses;
	protected static Logger logger;
	
	
	@Override
	public String getName() {
		return "firewall";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
		        new ArrayList<Class<? extends IFloodlightService>>();
		    l.add(IFloodlightProviderService.class);
		    return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
	    macAddresses = new ConcurrentSkipListSet<Long>();
	    logger = LoggerFactory.getLogger(Firewall.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
	        case PACKET_IN:
	            IRoutingDecision decision = null;
	            if (cntx != null) {
	            	decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
	
	            	return this.processPacketInMessage(sw,
	                                               (OFPacketIn) msg,
	                                               decision,
	                                               cntx);
	            }
		}
		
        return Command.CONTINUE;
	}
	
	public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

		if (decision == null) {
			//logger.info("Routing decision not taken yet");
		}
		
        /*Long sourceMACHash = Ethernet.toLong(eth.getSourceMACAddress());
        if (!macAddresses.contains(sourceMACHash)) {
            macAddresses.add(sourceMACHash);
            logger.info("MAC Address: {} seen on switch: {}",
                    HexString.toHexString(sourceMACHash),
                    sw.getId());
        }*/
		
		IPacket pkt = (IPacket) eth.getPayload();
		if (pkt != null && pkt instanceof IPv4) {
			IPv4 p = (IPv4) pkt;
			IPacket ppl = p.getPayload();
			if (ppl != null && ppl instanceof TCP) {
				TCP pp = (TCP) ppl;
				if (pp.getSourcePort() == 80 || pp.getDestinationPort() == 80) {
					if (decision == null) {
						decision = new FirewallDecision(IRoutingDecision.RoutingAction.DROP);
						decision.setWildcards(OFMatch.OFPFW_ALL
								& ~OFMatch.OFPFW_DL_SRC
			                    & ~OFMatch.OFPFW_IN_PORT
			                    & ~OFMatch.OFPFW_DL_VLAN
			                    & ~OFMatch.OFPFW_DL_DST
			                    & ~OFMatch.OFPFW_DL_TYPE
			                    & ~OFMatch.OFPFW_NW_PROTO
			                    & ~OFMatch.OFPFW_TP_SRC
			                    & ~OFMatch.OFPFW_NW_SRC_ALL
			                    & ~OFMatch.OFPFW_NW_DST_ALL);
						decision.addToContext(cntx);
						//logger.info("took decision to drop packet");
					}
				}
				logger.info("TCP SrcPort: {} DstPort: {}", pp.getSourcePort(), pp.getDestinationPort());
			} else if (ppl != null && ppl instanceof ICMP) {
				if (decision == null) {
					decision = new FirewallDecision(IRoutingDecision.RoutingAction.DROP);
					decision.addToContext(cntx);
					//logger.info("took decision to drop ICMP packet");
				}
			}
		}
        
        return Command.CONTINUE;
    }

}
