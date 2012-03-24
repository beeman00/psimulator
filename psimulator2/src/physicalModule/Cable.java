/*
 * created 28.10.2011
 */
package physicalModule;

import dataStructures.L2Packet;
import logging.Loggable;
import logging.Logger;
import logging.LoggingCategory;
import utils.SmartRunnable;
import utils.WorkerThread;

/**
 * Represents cable
 *
 * @author Stanislav Rehak <rehaksta@fit.cvut.cz>
 */
public class Cable implements SmartRunnable, Loggable {

	public final int configID;	// id z konfiguraku
	/**
	 * ID of first connector device.
	 * (pro posilani paketu Martinovi)
	 */
	private int idFirstDevice;
	/**
	 * ID of second connector device.
	 * (pro posilani paketu Martinovi)
	 */
	private int idSecondDevice;

	private SimulatorSwitchport firstCon;
	private SimulatorSwitchport secondCon;

	protected final WorkerThread worker;

	/**
	 * Delay in milliseconds
	 */
	private long delay;

	/**
	 * Creates cable with given delay time.
	 * @param id
	 * @param delay
	 */
	public Cable(int id, long delay) {
		this.configID = id;
		this.delay = delay;
		this.worker = new WorkerThread(this);
	}

	/*
	 * Sets first connector with given interface.
	 * Also sets interface's connector to correct connector.
	 * @param iface cannot be null
	 * @return true if connector was empty and now is connected to interface.
	 */
	public void setFirstSwitchport(SimulatorSwitchport swport) {
		swport.cabel = this;
		this.firstCon = swport;
	}

	/*
	 * Sets second connector with given interface.
	 * Also sets interface's connector to correct connector.
	 * @param iface cannot be null
	 * @return true if connector was empty and now is connected to interface.
	 */
	public void setSecondSwitchport(SimulatorSwitchport swport) {
		swport.cabel = this;
		this.secondCon = swport;
	}

	@Override
	public void doMyWork() {
		L2Packet packet;
		boolean firstIsEmpty = true;
		boolean secondIsEmpty = true;

		do {
			SimulatorSwitchport first = firstCon; // mohlo by to byt vne while-cyklu, ale co kdyz nekdo zapoji kabel (konektor) do rozhrani a my budem chtit, aby se to rozjelo?
			SimulatorSwitchport second = secondCon;

			if ((first != null) && !first.isEmptyBuffer()) {
				packet = first.popPacket();
				if (second != null) {
					makeDelay();
					Logger.log(this, Logger.INFO, LoggingCategory.CABEL_SENDING, "Sending packet through cabel..", new CableItem(packet, idFirstDevice, idSecondDevice, configID));
					second.receivePacket(packet);
				}
				firstIsEmpty = first.isEmptyBuffer();
			}

			if ((second != null) && !second.isEmptyBuffer()) {
				packet = second.popPacket();
				if (first != null) {
					makeDelay();
					Logger.log(this, Logger.INFO, LoggingCategory.CABEL_SENDING, "Sending packet through cabel..", new CableItem(packet, idSecondDevice, idFirstDevice, configID));
					first.receivePacket(packet);
				}
				secondIsEmpty = second.isEmptyBuffer();
			}

		} while (!firstIsEmpty || !secondIsEmpty);
	}

	private void makeDelay() {
		try {
			Thread.sleep(delay);
		} catch (InterruptedException ex) {
			// ok
		}
	}

	public void setFirstDeviceId(Integer id) {
		this.idFirstDevice = id;
	}

	public void setSecondDeviceId(Integer id) {
		this.idSecondDevice = id;
	}

	/**
	 * Vraci to switchport na druhym konci kabelu nez je ten zadanej.
	 * Returns switchport on the other end of the cable, where is the given.
	 * @param one
	 * @return
	 */
	public SimulatorSwitchport getTheOtherSwitchport(Switchport one) {
		if (one == firstCon) {
			return secondCon;
		} else if (one == secondCon) {
			return firstCon;
		} else {
			Logger.log(Logger.ERROR, LoggingCategory.PHYSICAL, "Byla spatne zavolana metoda getTheOtherSwitchport na kabelu s configID " + configID);
			return null;
		}
	}

	@Override
	public String getDescription() {
		return "Cable: sourceID=" + idFirstDevice + " " + "destinationID=" + idSecondDevice;
	}

	public class CableItem {
		public final L2Packet packet;
		public final int sourceID;
		public final int destinationID;
		public final int cabelID;

		public CableItem(L2Packet packet, int source_ID, int destination_ID, int cabel_ID) {
			this.packet = packet;
			this.sourceID = source_ID;
			this.destinationID = destination_ID;
			this.cabelID = cabel_ID;
		}
	}
}