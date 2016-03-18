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
 * @sfera.event_id message
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

	/**
	 * Returns the {@link Message} object representing the message that
	 * triggered this event
	 * 
	 * @return the {@link Message} object representing the message that
	 *         triggered this event
	 */
	@Override
	public Message getValue() {
		return message;
	}

	/**
	 * Returns the text of the message that triggered this event, if any.
	 * 
	 * @return the text of the message that triggered this event, or
	 *         {@code null} if the message did not contain any text
	 */
	@Override
	public String getScriptConditionValue() {
		return message.getText();
	}

}
