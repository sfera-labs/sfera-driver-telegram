/**
 * 
 */
package cc.sferalabs.sfera.drivers.telegram.events;

import cc.sferalabs.libs.telegram.bot.api.types.Message;
import cc.sferalabs.sfera.drivers.telegram.Telegram;
import cc.sferalabs.sfera.events.BaseEvent;

/**
 * Event triggered when the Telegram Bot receives a message.
 * 
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public class TelegramMessageEvent extends BaseEvent implements TelegramEvent {

	private final Message message;

	/**
	 * 
	 * @param source
	 *            source driver
	 * @param message
	 *            received message
	 */
	public TelegramMessageEvent(Telegram source, Message message) {
		super(source, "message");
		this.message = message;
	}

	@Override
	public Message getValue() {
		return message;
	}

	@Override
	public String getScriptConditionValue() {
		return message.getText();
	}

}
