/*-
 * +======================================================================+
 * Telegram
 * ---
 * Copyright (C) 2016-2021 Sfera Labs S.r.l.
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
import cc.sferalabs.libs.telegram.bot.api.types.Update;
import cc.sferalabs.sfera.drivers.telegram.Telegram;
import cc.sferalabs.sfera.events.BaseEvent;

/**
 * Event triggered when the Telegram Bot receives an update not containing a
 * {@link Message}.
 * 
 * @sfera.event_id update
 * @sfera.event_val update_obj see getValue()
 * 
 */
public class TelegramUpdateEvent extends BaseEvent implements TelegramEvent {

	private final Update update;

	/**
	 * 
	 * @param source
	 *            source driver
	 * @param update
	 *            received update
	 */
	public TelegramUpdateEvent(Telegram source, Update update) {
		super(source, "update");
		this.update = update;
	}

	/**
	 * Returns the {@link Update} object that triggered this event
	 * 
	 * @return the {@link Update} object that triggered this event
	 */
	@Override
	public Update getValue() {
		return update;
	}

}
