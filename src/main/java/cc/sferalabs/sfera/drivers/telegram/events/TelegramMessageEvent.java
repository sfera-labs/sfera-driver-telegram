/*-
 * +======================================================================+
 * Telegram
 * ---
 * Copyright (C) 2016 Sfera Labs S.r.l.
 * ---
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * -======================================================================-
 */

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
 * @sfera.event_val message_obj see getValue()
 * @sfera.event_val_simple message_text see getSimpleValue()
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
	public String getSimpleValue() {
		return message.getText();
	}

}
