/*
 * created 31.1.2012
 */
package networkModule.L3;

import dataStructures.*;
import dataStructures.ipAddresses.IPwithNetmask;
import dataStructures.ipAddresses.IpAddress;
import java.util.*;
import logging.Loggable;
import logging.Logger;
import logging.LoggingCategory;
import networkModule.L2.EthernetInterface;
import networkModule.L3.RoutingTable.Record;
import networkModule.L3.nat.NatTable;
import networkModule.L4.IcmpHandler;
import networkModule.TcpIpNetMod;
import psimulator2.Psimulator;
import utils.SmartRunnable;
import utils.Util;
import utils.Wakeable;
import utils.WorkerThread;

/**
 * Represents IP layer of ISO/OSI model.
 *
 * @author Stanislav Rehak <rehaksta@fit.cvut.cz>
 */
public abstract class IPLayer implements SmartRunnable, Loggable, Wakeable {

	protected WorkerThread worker;
	/**
	 * ARP cache table.
	 */
	protected final ArpCache arpCache;
	/**
	 * Packet filter. Controls NetworkAddressTranslation, packet dropping, ..
	 */
	protected final PacketFilter packetFilter = new PacketFilter(this);
	/**
	 * Buffer for incomming packets from L2.
	 */
	private final List<ReceiveItem> receiveBuffer = Collections.synchronizedList(new LinkedList<ReceiveItem>());
	/**
	 * Buffer for packets to by sent from L4.
	 */
	private final List<SendItem> sendBuffer = Collections.synchronizedList(new LinkedList<SendItem>());
	/**
	 * Buffer for packet without MAC address nexthop. ARP request was sent and packets are waiting for the reply.
	 */
	private final List<StoreItem> storeBuffer = new LinkedList<>();
	/**
	 * Routing table with record.
	 */
	public final RoutingTable routingTable = new RoutingTable();
	/**
	 * Link to network module.
	 */
	protected final TcpIpNetMod netMod;
	/**
	 * When some ARP reply arrives this is set to true so doMyWork() can process storeBuffer. After processing
	 * storeBuffer it is set to false.
	 */
	protected transient boolean newArpReply = false;
	/**
	 * Map of network interfaces. <br />
	 * Key - interface name <br />
	 * Value - interface
	 */
	private final Map<String, NetworkInterface> networkIfaces = new HashMap<>();
	/**
	 * Waiting time [ms] for ARP requests.
	 */
	private long arpTTL = 3_000;
	/**
	 * Default TTL values.
	 */
	public int ttl; // different for particular IPLayers
	/**
	 * Constructor of IP layer.
	 * Empty routing table is also created.
	 * @param netMod
	 */
	public IPLayer(TcpIpNetMod netMod) {
		this.netMod = netMod;
		this.arpCache = new ArpCache(netMod.getDevice());
		this.worker = new WorkerThread(this);
	}

	/**
	 * Getter for cisco & linux listing.
	 *
	 * @return
	 */
	public HashMap<IpAddress, ArpCache.ArpRecord> getArpCache() {
		return arpCache.getCache();
	}

	/**
	 * Getter for Cisco commands.
	 * @return
	 */
	public NatTable getNatTable() {
		return packetFilter.getNatTable();
	}

	public TcpIpNetMod getNetMod() {
		return netMod;
	}

	public IcmpHandler getIcmpHandler() {
		return netMod.transportLayer.icmphandler;
	}

	/**
	 * Method for receiving packet from layer 2.
	 *
	 * @param packet
	 * @param iface
	 */
	public void receivePacket(L3Packet packet, EthernetInterface iface) {
		receiveBuffer.add(new ReceiveItem(packet, iface));
		worker.wake();
	}

	/**
	 * Called from doMyWork() and from plaform-specific IPLayeres.
	 *
	 * @param packet
	 * @param iface incomming ethernet interface
	 */
	protected void handleReceivePacket(L3Packet packet, EthernetInterface iface) {
		switch (packet.getType()) {
			case ARP:
				ArpPacket arp = (ArpPacket) packet;
				handleReceiveArpPacket(arp, iface);
				break;

			case IPv4:
				IpPacket ip = (IpPacket) packet;
				handleReceiveIpPacket(ip, iface);
				break;

			default:
				Logger.log(this, Logger.WARNING, LoggingCategory.IP_LAYER, "Unsupported L3 type packet: " + packet.getType()+", dropping packet: ", packet);
		}
	}

	/**
	 * Handles imcomming ARP packets.
	 *
	 * @param packet
	 * @param iface incomming EthernetInterface
	 */
	protected abstract void handleReceiveArpPacket(ArpPacket packet, EthernetInterface iface);

	/**
	 * Process storeBuffer which is for packets without known MAC nextHop.
	 */
	private void handleStoreBuffer() {
		Logger.log(this, Logger.DEBUG, LoggingCategory.IP_LAYER, "handleStoreBuffer(), size: "+storeBuffer.size(), null);

		long now = System.currentTimeMillis();

		List<StoreItem> old = new ArrayList<>();
		List<StoreItem> serve = new ArrayList<>();
		StoreItem m;
		Iterator<StoreItem> it = storeBuffer.iterator();
		Map<IpAddress, MacAddress> temp = new HashMap<>();

		while (it.hasNext()) {
			m = it.next();

			if (now - m.timeStamp >= arpTTL) { // vice jak arpTTL [s] stare se smaznou, tak se posle zpatky DHU
				// vyndat z bufferu
				old.add(m);
				continue;
			}

			MacAddress mac = arpCache.getMacAdress(m.nextHop);
			if (mac != null) {
				// obslouzit
				temp.put(m.nextHop, mac);
				serve.add(m);
			}

			Logger.log(this, Logger.DEBUG, LoggingCategory.ARP, "This record has not timedout nor ARP answer has come. age: " + (now - m.timeStamp) + ", will delete:: " + arpTTL, null);
		}

		storeBuffer.removeAll(old);
		storeBuffer.removeAll(serve);

		for (StoreItem o : old) {
			Logger.log(this, Logger.INFO, LoggingCategory.NET, "Dropping packet: ARP reply was not received. Will send Destination Unreachable.", o.packet);
			getIcmpHandler().sendDestinationHostUnreachable(o.packet.src, o.packet);
		}

		for (StoreItem s : serve) {
			MacAddress mac = temp.get(s.nextHop);
			Logger.log(this, Logger.INFO, LoggingCategory.NET, "ARP reply received, sending packet to nextHop.", s.packet);
			netMod.ethernetLayer.sendPacket(s.packet, s.out, mac);
		}

		newArpReply = false;
	}

	/**
	 * Handles packet: is it for me?, routing, decrementing TTL, postrouting, MAC address finding.
	 *
	 * @param packet
	 * @param iface incomming EthernetInterface, can be null
	 */
	protected abstract void handleReceiveIpPacket(IpPacket packet, EthernetInterface iface);

	/**
	 * Vezme packet, pusti se na nej postRouting, zjisti MAC cile a preda ethernetovy vrstve.
	 *
	 * @param packet co chci odeslaat
	 * @param record zaznam z RT
	 * @param ifaceIn prichozi rozhrani, null pokud odesilam novy paket
	 */
	protected void processPacket(IpPacket packet, Record record, NetworkInterface ifaceIn) {
		// zanatuj
		packet = packetFilter.postRouting(packet, ifaceIn, record.rozhrani); // prichozi rozhrani je null, protoze zadne takove neni
		if (packet == null) { // packet dropped
			return;
		}

		// zjistit nexHopIp
		// kdyz RT vrati record s branou, tak je nextHopIp record.brana
		// kdyz je record bez brany, tak je nextHopIp uz ta hledana IP adresa
		IpAddress nextHopIp = packet.dst;
		if (record.brana != null) {
			nextHopIp = record.brana;
		}

		// zjisti MAC adresu z ARP cache - je=OK, neni=vygenerovat ARP request a vlozit do storeBuffer + touch na sendItem, ktera bude v storeBuffer
		MacAddress nextHopMac = arpCache.getMacAdress(nextHopIp);
		if (nextHopMac == null) { // posli ARP request a dej do fronty
			ArpPacket arpPacket = new ArpPacket(record.rozhrani.ipAddress.getIp(), record.rozhrani.getMacAddress(), nextHopIp);

			Logger.log(this, Logger.INFO, LoggingCategory.ARP, "Cannot send packet to address: " + packet.dst + ", becaouse nextHop MAC address is unknown. Sending ARP request via interface: "
					+ record.rozhrani.name, arpPacket);
			netMod.ethernetLayer.sendPacket(arpPacket, record.rozhrani.ethernetInterface, MacAddress.broadcast());

			storeBuffer.add(new StoreItem(packet, record.rozhrani.ethernetInterface, nextHopIp));
			Psimulator.getPsimulator().budik.registerWake(this, arpTTL);
			return;
		}

		// kdyz to doslo az sem, tak muzu odeslat..
		Logger.log(this, Logger.INFO, LoggingCategory.NET, "Sending packet.", packet);
		netMod.ethernetLayer.sendPacket(packet, record.rozhrani.ethernetInterface, nextHopMac);

	}

	/**
	 * Method for sending packet from layer 4 with system default TTL.
	 *
	 * @param packet data to be sent
	 * @param dst destination address
	 */
	public void sendPacket(L4Packet packet, IpAddress dst) {
		sendPacket(packet, dst, this.ttl);
	}

	/**
	 * Method for sending packet from layer 4.
	 *
	 * @param packet data to be sent
	 * @param dst destination address
	 * @param ttl Time To Live value
	 */
	public void sendPacket(L4Packet packet, IpAddress dst, int ttl) {
		sendBuffer.add(new SendItem(packet, dst, ttl));
		worker.wake();
	}

	/**
	 * Method for sending from IPLayer and IcmpHandler only! <br />
	 * In this method everything is in the same thread. <br />
	 * Don't use it from Layer 4!
	 *
	 * @param packet data to be sent
	 * @param dst destination address
	 */
	public abstract void handleSendPacket(L4Packet packet, IpAddress dst);

	@Override
	public void doMyWork() {
//		Logger.log(this, Logger.DEBUG, LoggingCategory.IP_LAYER, "doMyWork()", null);

		// prochazet jednotlivy buffery a vyrizovat jednotlivy pakety
		while (!sendBuffer.isEmpty() || !receiveBuffer.isEmpty()) {
			if (!receiveBuffer.isEmpty()) {
				Logger.log(this, Logger.DEBUG, LoggingCategory.IP_LAYER, "doMyWork() receiveBuffer", null);
				ReceiveItem m = receiveBuffer.remove(0);
				handleReceivePacket(m.packet, m.iface);
			}

			if (!sendBuffer.isEmpty()) {
				Logger.log(this, Logger.DEBUG, LoggingCategory.IP_LAYER, "doMyWork() sendBuffer", null);
				SendItem m = sendBuffer.remove(0);
				handleSendPacket(m.packet, m.dst); // bude se obsluhovat platform-specific
			}

			if (newArpReply && !storeBuffer.isEmpty()) { // ten boolean tam je proto, aby se to neprochazelo v kazdym cyklu
				// bude obskoceno vzdy, kdyz se tam neco prida, tak snad ok
				handleStoreBuffer();
			}
		}

		if (!storeBuffer.isEmpty()) { // ?
			// bude obskoceno vzdy, kdyz se tam neco prida, tak snad ok
			handleStoreBuffer();
		}
	}

	/**
	 * Sets IpAddress with NetMask to given interface. There is no other way to set IP address to interface.
	 *
	 * Reason for this method is that we might to add some actions after setting address in future.
	 *
	 * @param iface
	 * @param ipAddress
	 */
	public void changeIpAddressOnInterface(NetworkInterface iface, IPwithNetmask ipAddress) {
		iface.ipAddress = ipAddress;
	}

	/**
	 * Adds Network interface to a list.
	 * This method is used only in loading configuration file.
	 * @param iface
	 */
	public void addNetworkInterface(NetworkInterface iface) {
		networkIfaces.put(iface.name, iface);
	}

	/**
	 * Returns true if targetIpAddress is on my NetworkInterface and isUp
	 *
	 * @param targetIpAddress
	 * @return
	 */
	protected boolean isItMyIpAddress(IpAddress targetIpAddress) {
		for (NetworkInterface iface : networkIfaces.values()) {
			if (iface.ipAddress != null && iface.ipAddress.getIp().equals(targetIpAddress) && iface.isUp) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if packet is ICMP REQUEST.
	 * zatim nemazat!s
	 * @param packet
	 * @return
	 */
	private boolean isPacketIcmpRequest(IpPacket packet) {
		if (packet.data != null && packet.data.getType() == L4Packet.L4PacketType.ICMP) {
			IcmpPacket p = (IcmpPacket) packet.data;
			if (p.type == IcmpPacket.Type.REQUEST) {
				return true;
			}

		}
		return false;
	}

	@Override
	public String getDescription() {
		return Util.zarovnej(netMod.getDevice().getName(), Util.deviceNameAlign) + " IPLayer";
	}

	/**
	 * Returns NetworkInterface which belongs to the EthernetInterface inc. <br />
	 * Returns null iff inc is null.
	 * @param inc
	 * @return
	 */
	protected NetworkInterface findIncommingNetworkIface(EthernetInterface inc) {
		if (inc == null) {
			return null;
		}
		NetworkInterface iface = getNetworkInteface(inc.name);
		if (iface == null) {
			Logger.log(this, Logger.WARNING, LoggingCategory.IP_LAYER, "No NetworkInterface is connected to this EthernetInterface from which was reveived packet. This should never happen!", null);
		}
		return iface;
	}

	/**
	 * Fill the routing table during simulator loading from addresses on interfaces.
	 * It should be called only if in the configuration file does'n exist any routing table configuration.
	 *
	 * Naplni routovaci tabulku dle adres na rozhranich, tak jak to dela linux.
	 * Volat jenom pri konfiguraci, nebyla-li routovaci tabulka ulozena.
	 */
	public void updateNewRoutingTable() {
		if (routingTable.size() != 0) {
			Logger.log(this, Logger.WARNING, LoggingCategory.IP_LAYER, "updateNewRoutingTable(): should be called only in case no saved setting in configuration file. "
					+ "But table is no empty!!!", null);
			return;
		}
		for (NetworkInterface iface : getNetworkIfaces()) {
			if (iface.getIpAddress() != null) {
				routingTable.addRecord(iface.ipAddress.getNetworkNumber(), iface);
			}
		}
	}

	/**
	 * Getter for Saver.
	 * @return
	 */
	public Collection<NetworkInterface> getNetworkIfaces() {
		return networkIfaces.values();
	}

	/**
	 * Return interface with name or null iff there is no such interface.
	 * @param name
	 * @return
	 */
	public NetworkInterface getNetworkInteface(String name) {
		return networkIfaces.get(name);
	}

	/**
	 * Return interface with name (ignores case) or null iff there is no such interface.
	 * @param name
	 * @return
	 */
	public NetworkInterface getNetworkIntefaceIgnoreCase(String name) {
		for (NetworkInterface iface : networkIfaces.values()) {
			if (iface.name.equalsIgnoreCase(name)) {
				return iface;
			}
		}
		return null;
	}

	/**
	 * Returns interfaces as collection sorted by interface name.
	 * @return
	 */
	public Collection<NetworkInterface> getSortedNetworkIfaces() {
		List<NetworkInterface> ifaces = new ArrayList<>(networkIfaces.values());
		Collections.sort(ifaces);
		return ifaces;
	}

	@Override
	public void wake() {
		Logger.log(this, Logger.DEBUG, LoggingCategory.IP_LAYER, "awaken by Alarm", null);
		newArpReply = true;
		this.worker.wake();
	}

	private class SendItem {

		final L4Packet packet;
		final IpAddress dst;
		final int ttl;

		public SendItem(L4Packet packet, IpAddress dst, int ttl) {
			this.packet = packet;
			this.dst = dst;
			this.ttl = ttl;
		}
	}

	private class ReceiveItem {

		final L3Packet packet;
		final EthernetInterface iface;

		public ReceiveItem(L3Packet packet, EthernetInterface iface) {
			this.packet = packet;
			this.iface = iface;
		}
	}

	private class StoreItem {
		/**
		 * Packet to send.
		 */
		final IpPacket packet;
		/**
		 * Outgoing interface (gained from routing table).
		 */
		final EthernetInterface out;
		/**
		 * IP of nextHop (gained from routing table).
		 */
		final IpAddress nextHop;
		/**
		 * Time stamp - for handling old records (drop packet + send DHU).
		 */
		long timeStamp;

		/**
		 * Store item.
		 * @param packet packet to send
		 * @param out outgoing interface (gained from routing table)
		 * @param nextHop IP of nextHop
		 */
		public StoreItem(IpPacket packet, EthernetInterface out, IpAddress nextHop) {
			this.packet = packet;
			this.nextHop = nextHop;
			this.out = out;
			this.timeStamp = System.currentTimeMillis();
		}
	}
}