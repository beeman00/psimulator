/*
 * created 2.2.2012
 */

package logging;

import java.util.EnumMap;
import java.util.Map;

/**
 * Main server listener.
 * Writes everything to server's console.
 *
 * @author Stanislav Rehak <rehaksta@fit.cvut.cz>
 * @author Tomáš Pitřinec
 */
public class SystemListener implements LoggerListener {

	final Map<LoggingCategory,Integer> configuration = new EnumMap<LoggingCategory, Integer>(LoggingCategory.class);

	public SystemListener() {
		ConfigureSystemListener.configure(configuration);
	}

	public void listen(Loggable caller, int logLevel, LoggingCategory category, String message, Object object) {
		try {
			if (logLevel <= configuration.get(category)) {
				System.out.println("[" + Logger.logLevelToString(logLevel) + "] " + category + ": " + caller.getDescription() + ": " + message);
			}
		} catch (NullPointerException e) {
			System.out.println("An error occured during logging:-) \n"
					+ "LoggingCategory was null.");
			System.exit(3);
		}
	}
}