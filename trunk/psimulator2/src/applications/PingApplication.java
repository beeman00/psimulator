/*
 * created 8.3.2012
 */

package applications;

import commands.ApplicationNotifiable;
import dataStructures.IcmpPacket;
import dataStructures.IpPacket;
import dataStructures.ipAddresses.IpAddress;
import device.Device;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import logging.Logger;
import logging.LoggingCategory;
import utils.Util;

/**
 * Represents abstract Ping application. <br />
 *
 * @author Stanislav Rehak <rehaksta@fit.cvut.cz>
 */
public abstract class PingApplication extends Application {

	protected IpAddress target;
	protected int count = 0;
	protected int size = 56; // default linux size (without header)
	protected int timeout = 10_000;
	protected Stats stats = new Stats();
	protected int seq = 1;
	protected final ApplicationNotifiable command;
	protected transient boolean allPacketArrived = false;
	/**
	 * Exit called from other thread.
	 */
	private transient boolean exitCalled = false;
	/**
	 * kdyz nebude zadan, tak se pouzije vychozi systemova hodnota ze sitoveho modulu
	 */
	protected Integer ttl = null;
	/**
	 * Time to wait between sending to pings.
	 */
	protected int waitTime = 1_000;
	/**
	 * Key - seq <br />
	 * Value - timestamp in ms
	 */
	protected Map<Integer, Long> timestamps = new HashMap<>();
	protected boolean [] sent;
	protected boolean [] recieved;

	public PingApplication(Device device, ApplicationNotifiable command) {
		super("ping", device);
		this.command = command;
	}



	public void setCount(int count) {
		this.count = count;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public void setTarget(IpAddress target) {
		this.target = target;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	/**
	 * Print stats and exits application.
	 */
	public abstract void printStats();

	@Override
	public void atStart() {
		if (target == null) {
			Logger.log(this, Logger.WARNING, LoggingCategory.GENERIC_APPLICATION, "PingApplication has no target! Exiting..", null);
			kill();
		}
		if (getPort() == null) {
			Logger.log(this, Logger.WARNING, LoggingCategory.GENERIC_APPLICATION, "PingApplication has no port assigned! Exiting..", null);
			kill();
		}

		Logger.log(this, Logger.DEBUG, LoggingCategory.PING_APPLICATION, getName()+" atStart()", null);
		startMessage();
		sendPings();
		if (!allPacketArrived) { // budik, pak smazat
			Util.sleep(timeout);
		}
		if (!exitCalled) {
			exit();
		}
	}

	/**
	 * Sends #count pings.
	 */
	private void sendPings() {
		if (count > 0) {
			this.sent = new boolean[count];
			this.recieved = new boolean[count];
		}

		for (int i = 0; i < count; i++) {
			Logger.log(this, Logger.DEBUG, LoggingCategory.PING_APPLICATION, getName()+" posilam ping seq="+seq, null);
			timestamps.put(seq, System.currentTimeMillis());
			sent[seq-1] = true;
			transportLayer.icmphandler.sendRequest(target, ttl, seq++, port);
			stats.odeslane++;

			if (exitCalled) {
				return;
			}

			if (i != count-1 || !allPacketArrived) {
				Util.sleep(waitTime);
			}
		}
	}

	@Override
	public void atExit() {
		this.exitCalled = true;
		stats.countStats();
		printStats();
		command.applicationFinished();
	}

	@Override
	public void doMyWork() {

		IcmpPacket packet;

		while (!buffer.isEmpty()) {
			IpPacket p = buffer.remove(0);
			if (! (p.data instanceof IcmpPacket)) {
				Logger.log(this, Logger.WARNING, LoggingCategory.PING_APPLICATION, "Dropping packet, because PingApplication recieved non ICMP packet", p);
				continue;
			}

			packet = (IcmpPacket) p.data;
			Long sendTime = timestamps.get(packet.seq);
			timestamps.remove(packet.seq); // odstranim uz ulozeny
			// TODO: resit duplikace prichozich paketu - kdyz mi prijde v poradku paket, tak ho napr. ulozim do Map<seq, packet>, kdyz zjistim, ze prisel podruhy, tak vypisu duplikacni hlasku

			if (sendTime == null) {
				Logger.log(this, Logger.WARNING, LoggingCategory.PING_APPLICATION, "Dropping packet, because PingApplication doesn't expect such a PING reply "
						+ "(IcmpPacket with this seq="+packet.seq+" was never send OR it was served in a past)", p);
				continue;
			}

			long delay = System.currentTimeMillis() - sendTime;
			if (delay <= timeout) { // ok, paket dorazil vcas
				Logger.log(this, Logger.DEBUG, LoggingCategory.PING_APPLICATION, getName()+" v poradku dorazil ping s seq="+packet.seq, packet);
				stats.odezvy.add(delay);
				stats.prijate++;
				handleIncommingPacket(packet);
			} else {
				Logger.log(this, Logger.DEBUG, LoggingCategory.PING_APPLICATION, getName()+" v poradku dorazil, ale mezitim vyprsel timeout, packet seq="+packet.seq, packet);
			}
		}
	}

	/**
	 * Handles incomming packet: REPLY, TIME_EXCEEDED, UNDELIVERED.
	 * Ma vypsat informace o
	 *
	 * @param packet
	 */
	protected abstract void handleIncommingPacket(IcmpPacket packet);

	/**
	 * Slouzi na hlasku o tom kolik ceho a kam posilam..
	 */
	protected abstract void startMessage();

	/**
	 * Class encapsulating packets statistics.
	 */
	public class Stats {

		/**
		 * pocet odeslanych paketu
		 */
		protected int odeslane = 0;
		/**
		 * pocet prijatych paketu
		 */
		protected int prijate = 0;
		/**
		 * Seznam odezev vsech prijatych icmp_reply.
		 */
		protected List<Long> odezvy = new ArrayList<>();
		/**
		 * Ztrata v procentech.
		 */
		protected int ztrata;
		/**
		 * Uspesnost v procentech.
		 */
		protected int uspech;
		/**
		 * pocet vracenejch paketu o chybach (tzn. typy 3 a 11)
		 */
		protected int errors;
		protected double min;
		protected double max;
		protected double avg;
		protected double celkovyCas; //soucet vsech milisekund

		/**
		 * Propocita min, avg, max, celkovyCas, ztrata.<br />
		 * Pro spravnou funkci staci, aby konkretni pingy delali 3 veci: <br />
		 * 1. pri odeslani icmp_req inkrementovat promennou odeslane <br />
		 * 2. pri prijeti icmp_reply pridat do seznamu odezvy cas paketu. <br />
		 * 3. pred dotazanim na statistiky zavolat tuto metodu countStats() <br />
		 */
		protected void countStats() {
			if (odezvy.size() >= 1) {
				min = odezvy.get(0);
				max = odezvy.get(0);

				double sum = 0;
				for (double d : odezvy) {
					if (d < min) {
						min = d;
					}
					if (d > max) {
						max = d;
					}
					sum += d;
				}

				avg = sum / odezvy.size();
				celkovyCas = sum;
			}
			if (odeslane > 0) {
				ztrata = 100 - (int) ((float) prijate / (float) odeslane * (float) 100);
				uspech = 100 - ztrata;
			}
		}
	}
}