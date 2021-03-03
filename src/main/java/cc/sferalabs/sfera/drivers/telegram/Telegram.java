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

package cc.sferalabs.sfera.drivers.telegram;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.simple.parser.ParseException;

import cc.sferalabs.libs.telegram.bot.api.ResponseError;
import cc.sferalabs.libs.telegram.bot.api.TelegramBot;
import cc.sferalabs.libs.telegram.bot.api.requests.Request;
import cc.sferalabs.libs.telegram.bot.api.requests.SendAudioRequest;
import cc.sferalabs.libs.telegram.bot.api.requests.SendChatActionRequest;
import cc.sferalabs.libs.telegram.bot.api.requests.SendDocumentRequest;
import cc.sferalabs.libs.telegram.bot.api.requests.SendMessageRequest;
import cc.sferalabs.libs.telegram.bot.api.requests.SendPhotoRequest;
import cc.sferalabs.libs.telegram.bot.api.types.ForceReply;
import cc.sferalabs.libs.telegram.bot.api.types.Message;
import cc.sferalabs.libs.telegram.bot.api.types.ReplyKeyboardHide;
import cc.sferalabs.libs.telegram.bot.api.types.ReplyKeyboardMarkup;
import cc.sferalabs.libs.telegram.bot.api.types.ReplyMarkup;
import cc.sferalabs.libs.telegram.bot.api.types.Update;
import cc.sferalabs.libs.telegram.bot.api.types.User;
import cc.sferalabs.sfera.core.Configuration;
import cc.sferalabs.sfera.drivers.Driver;
import cc.sferalabs.sfera.drivers.telegram.events.TelegramMessageEvent;
import cc.sferalabs.sfera.events.Bus;

/**
 * Telegram Bot API driver
 * 
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public class Telegram extends Driver {

	private static final int POLLING_TIMEOUT = 60;
	private static final int REQUEST_TIMEOUT = 10000;
	private Path authorizedUsersFile;
	private TelegramBot telegram;
	private Long offset = null;
	private String botSecret;
	private final Set<Long> authorizedUsers = new HashSet<>();
	private long messagesTtl;

	public Telegram(String id) {
		super(id);
	}

	@Override
	protected boolean onInit(Configuration config) throws InterruptedException {
		String token = config.get("token", null);
		if (token == null) {
			log.error("Parameter 'token' not found in configuration");
			return false;
		}
		botSecret = config.get("secret", null);
		telegram = new TelegramBot(token);
		try {
			String botName = telegram.getBotName(5000);
			log.info("Connected to bot: " + botName);
		} catch (IOException | ParseException | ResponseError e) {
			log.error("Error reaching Telegram service", e);
			return false;
		}
		messagesTtl = config.get("messagesTtl", 10);
		messagesTtl *= 1000;

		try {
			synchronized (authorizedUsers) {
				authorizedUsersFile = getDriverInstanceDataDir().resolve("users");
				List<String> lines = Files.readAllLines(authorizedUsersFile);
				for (String line : lines) {
					if (!line.isEmpty()) {
						long u = Long.parseLong(line);
						if (u > 0) {
							authorizedUsers.add(u);
						}
					}
				}
			}
		} catch (NoSuchFileException e) {
			log.debug("Authorized users data not found");
		} catch (IOException e) {
			log.error("Error loading users", e);
			return false;
		}

		return true;
	}

	@Override
	protected boolean loop() throws InterruptedException {
		try {
			List<Update> updates = telegram.pollUpdates(offset, null, POLLING_TIMEOUT);
			updates.forEach(update -> {
				for (int i = 0; i < 3; i++) {
					try {
						processMessage(update.getMessage());
						break;
					} catch (Exception e) {
						log.error("Error processing update " + update, e);
					}
				}

				long updateId = update.getUpdateId();
				if (offset == null || updateId >= offset) {
					offset = updateId + 1;
				}
			});

		} catch (IOException | ParseException e) {
			log.error("Polling error", e);
			return false;
		} catch (ResponseError e) {
			log.error("Response error", e);
			return false;
		}

		return true;
	}

	/**
	 * @param message
	 * @throws Exception
	 */
	private void processMessage(Message message) throws Exception {
		long date = message.getInt("date");
		String text = message.getText();
		User user = message.getFrom();
		long userId = user.getId();

		if (System.currentTimeMillis() > date * 1000 + messagesTtl) {
			log.warn("Message discarded from {}: {}", userId, text);
			return;
		}

		log.debug("Message from {}: {}", userId, text);

		if (botSecret != null) {
			synchronized (authorizedUsers) {
				if (!authorizedUsers.contains(userId)) {
					if (text != null && text.startsWith("/addme ")) {
						String secret = text.substring(7).trim();
						if (secret.equals(botSecret)) {
							addAuthorizedUser(userId, user.getFirstName());
							sendMessage(userId, "OK");
						} else {
							log.warn("User {} attempted to add himself to this bot", userId);
						}
						return;
					}

					log.warn("Message from unauthorized user {}: {}", userId, text);
					return;
				}
			}
		}

		Bus.post(new TelegramMessageEvent(this, message));
	}

	/**
	 * 
	 * @param id
	 * @param name
	 * @throws IOException
	 */
	private void addAuthorizedUser(long id, String name) throws IOException {
		synchronized (authorizedUsers) {
			authorizedUsers.add(id);

			String users = String.join("\n",
					authorizedUsers.stream().map(i -> String.valueOf(i)).collect(Collectors.<String>toList()));
			if (!Files.exists(authorizedUsersFile)) {
				Files.createDirectories(authorizedUsersFile.getParent());
			}
			try (BufferedWriter writer = Files.newBufferedWriter(authorizedUsersFile)) {
				writer.write(users);
			}
		}
		log.info("Added authorized user: {} ({})", id, name);
	}

	@Override
	protected void onQuit() {
		telegram = null;
	}

	/**
	 * 
	 * @param req
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 * @throws ResponseError
	 */
	private <T> T sendRequest(Request req) throws IOException, ParseException, ResponseError {
		if (telegram == null) {
			throw new IOException("Driver quitted");
		}
		return telegram.sendRequest(req, REQUEST_TIMEOUT);
	}

	/**
	 * @param replyMarkup
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private ReplyMarkup toReplyMarkup(Map<String, Object> map) {
		if (map == null) {
			return null;
		}
		Object keyboard = map.get("keyboard");
		if (keyboard != null) {
			Boolean resizeKeyboard = (Boolean) map.get("resize_keyboard");
			Boolean oneTimeKeyboard = (Boolean) map.get("one_time_keyboard");
			Boolean selective = (Boolean) map.get("selective");
			String[][] kb = null;
			if (keyboard instanceof String[][]) {
				kb = (String[][]) keyboard;
			}
			if (keyboard instanceof Map) {
				kb = new String[((Map<Object, Object>) keyboard).size()][];
				int l = 0;
				for (Object line : ((Map<Object, Object>) keyboard).values()) {
					Collection<String> keys = ((Map<Object, String>) line).values();
					kb[l] = new String[keys.size()];
					int k = 0;
					for (String key : keys) {
						kb[l][k] = key;
						k++;
					}
					l++;
				}
			}
			if (kb != null) {
				return new ReplyKeyboardMarkup(kb, resizeKeyboard, oneTimeKeyboard, selective);
			}
		}
		Object hideKeyboard = map.get("hide_keyboard");
		if (hideKeyboard == Boolean.TRUE) {
			Boolean selective = (Boolean) map.get("selective");
			return new ReplyKeyboardHide(selective);
		}
		Object forceReply = map.get("force_reply");
		if (forceReply == Boolean.TRUE) {
			Boolean selective = (Boolean) map.get("selective");
			return new ForceReply(selective);
		}
		throw new IllegalArgumentException();
	}

	/**
	 * Sends a text message to the specified chat or to all authorized users.
	 * <p>
	 * Refer to: https://core.telegram.org/bots/api#sendmessage
	 * 
	 * @param chatId
	 *            Unique identifier for the target chat or {@code 0} to sent to all
	 *            authorized users
	 * @param text
	 *            Text of the message to be sent
	 * @throws ResponseError
	 *             if the server returned an error response
	 * @throws ParseException
	 *             if an error occurs while parsing the server response
	 * @throws IOException
	 *             if an I/O exception occurs
	 */
	public void sendMessage(long chatId, String text) throws IOException, ParseException, ResponseError {
		sendMessage(chatId, text, null, null, null, null);
	}

	/**
	 * Sends a text message to the specified chat or to all authorized users.
	 * <p>
	 * Optional parameters can be set to {@code null} for default behaviors.
	 * <p>
	 * Refer to: https://core.telegram.org/bots/api#sendmessage
	 * 
	 * @param chatId
	 *            Unique identifier for the target chat or {@code 0} to sent to all
	 *            authorized users
	 * @param text
	 *            Text of the message to be sent
	 * @param parseMode
	 *            "Markdown" or "HTML"
	 * @param disableWebPagePreview
	 *            if {@code true}, disables link previews for links in this message
	 * @param replyToMessageId
	 *            If the message is a reply, ID of the original message
	 * @param replyMarkup
	 *            Map representing the 'reply_markup' parameter.
	 *            <p>
	 *            Refer to:
	 *            <ul>
	 *            <li>https://core.telegram.org/bots/api#replykeyboardmarkup</li>
	 *            <li>https://core.telegram.org/bots/api#replykeyboardhide</li>
	 *            <li>https://core.telegram.org/bots/api#forcereply</li>
	 *            </ul>
	 * 
	 *            Java example:
	 * 
	 *            <pre>
	 *            Map&lt;String, Object&gt; replyMarkup = new HashMap&lt;&gt;();
	 *            replyMarkup.put("keyboard", new String[][] { { "1", "2" }, { "3", "4" } });
	 *            replyMarkup.put("one_time_keyboard", true);
	 *            </pre>
	 * 
	 *            Script example:
	 * 
	 *            <pre>
	 *            var replyMarkup = {};
	 *            replyMarkup['keyboard'] = [["1","2"],["3","4"]];
	 *            replyMarkup['one_time_keyboard'] = true;
	 *            </pre>
	 * 
	 * @throws ResponseError
	 *             if the server returned an error response
	 * @throws ParseException
	 *             if an error occurs while parsing the server response
	 * @throws IOException
	 *             if an I/O exception occurs
	 */
	public void sendMessage(long chatId, String text, String parseMode, Boolean disableWebPagePreview,
			Long replyToMessageId, Map<String, Object> replyMarkup) throws IOException, ParseException, ResponseError {
		if (chatId == 0) {
			synchronized (authorizedUsers) {
				for (Long u : authorizedUsers) {
					sendMessage(u, text, parseMode, disableWebPagePreview, replyToMessageId, replyMarkup);
				}
			}
		} else {
			log.debug("Sending message to {}: {}", chatId, text);
			sendRequest(new SendMessageRequest(chatId, text, parseMode, disableWebPagePreview, replyToMessageId,
					toReplyMarkup(replyMarkup)));
		}
	}

	/**
	 * Sends a chat action to the specified chat.
	 * <p>
	 * Refer to: https://core.telegram.org/bots/api#sendchataction
	 * 
	 * @param chatId
	 *            Unique identifier for the target chat
	 * @param action
	 *            Type of action to broadcast: "typing", "upload_photo",
	 *            "record_video", "upload_video", "record_audio", "upload_audio",
	 *            "upload_document", or "find_location".
	 * @throws ResponseError
	 *             if the server returned an error response
	 * @throws ParseException
	 *             if an error occurs while parsing the server response
	 * @throws IOException
	 *             if an I/O exception occurs
	 */
	public void sendChatAction(long chatId, String action) throws IOException, ParseException, ResponseError {
		log.debug("Sending chat action to {}: {}", chatId, action);
		sendRequest(new SendChatActionRequest(chatId, action));
	}

	/**
	 * Sends an image to the specified chat.
	 * <p>
	 * Optional parameters can be set to {@code null} for default behaviors.
	 * <p>
	 * Refer to: https://core.telegram.org/bots/api#sendphoto
	 * 
	 * @param chatId
	 *            Unique identifier for the target chat
	 * @param path
	 *            Path of the image file to send
	 * @param caption
	 *            Photo caption
	 * @throws ResponseError
	 *             if the server returned an error response
	 * @throws ParseException
	 *             if an error occurs while parsing the server response
	 * @throws IOException
	 *             if an I/O exception occurs
	 */
	public void sendPhoto(long chatId, String path, String caption) throws IOException, ParseException, ResponseError {
		sendPhoto(chatId, path, caption, null, null);
	}

	/**
	 * Sends an image to the specified chat.
	 * <p>
	 * Optional parameters can be set to {@code null} for default behaviors.
	 * <p>
	 * Refer to: https://core.telegram.org/bots/api#sendphoto
	 * 
	 * @param chatId
	 *            Unique identifier for the target chat
	 * @param path
	 *            Path of the image file to send
	 * @param caption
	 *            Photo caption
	 * @param replyToMessageId
	 *            If the message is a reply, ID of the original message
	 * @param replyMarkup
	 *            Map representing the 'reply_markup' parameter. See
	 *            {@link #sendMessage(long, String, String, Boolean, Long, Map)} for
	 *            details
	 * @throws ResponseError
	 *             if the server returned an error response
	 * @throws ParseException
	 *             if an error occurs while parsing the server response
	 * @throws IOException
	 *             if an I/O exception occurs
	 */
	public void sendPhoto(long chatId, String path, String caption, Long replyToMessageId,
			Map<String, Object> replyMarkup) throws IOException, ParseException, ResponseError {
		log.debug("Sending image to {}: {}", chatId, path);
		sendRequest(
				new SendPhotoRequest(chatId, Paths.get(path), caption, replyToMessageId, toReplyMarkup(replyMarkup)));
	}

	/**
	 * Sends an audio file to the specified chat.
	 * <p>
	 * Optional parameters can be set to {@code null} for default behaviors.
	 * <p>
	 * Refer to: https://core.telegram.org/bots/api#sendaudio
	 * 
	 * @param chatId
	 *            Unique identifier for the target chat
	 * @param path
	 *            Path of the audio file to send
	 * @param title
	 *            Track name
	 * @throws ResponseError
	 *             if the server returned an error response
	 * @throws ParseException
	 *             if an error occurs while parsing the server response
	 * @throws IOException
	 *             if an I/O exception occurs
	 */
	public void sendAudio(long chatId, String path, String title) throws IOException, ParseException, ResponseError {
		sendAudio(chatId, path, null, null, title, null, null);
	}

	/**
	 * Sends an audio file to the specified chat.
	 * <p>
	 * Optional parameters can be set to {@code null} for default behaviors.
	 * <p>
	 * Refer to: https://core.telegram.org/bots/api#sendaudio
	 * 
	 * @param chatId
	 *            Unique identifier for the target chat
	 * @param path
	 *            Path of the audio file to send
	 * @param duration
	 *            Duration of the audio in seconds
	 * @param performer
	 *            Performer
	 * @param title
	 *            Track name
	 * @param replyToMessageId
	 *            If the message is a reply, ID of the original message
	 * @param replyMarkup
	 *            Map representing the 'reply_markup' parameter. See
	 *            {@link #sendMessage(long, String, String, Boolean, Long, Map)} for
	 *            details
	 * @throws ResponseError
	 *             if the server returned an error response
	 * @throws ParseException
	 *             if an error occurs while parsing the server response
	 * @throws IOException
	 *             if an I/O exception occurs
	 */
	public void sendAudio(long chatId, String path, Integer duration, String performer, String title,
			Long replyToMessageId, Map<String, Object> replyMarkup) throws IOException, ParseException, ResponseError {
		log.debug("Sending audio to {}: {}", chatId, path);
		sendRequest(new SendAudioRequest(chatId, Paths.get(path), duration, performer, title, replyToMessageId,
				toReplyMarkup(replyMarkup)));
	}

	/**
	 * Sends a general file to the specified chat.
	 * <p>
	 * Refer to: https://core.telegram.org/bots/api#senddocument
	 * 
	 * @param chatId
	 *            Unique identifier for the target chat
	 * @param path
	 *            Path of the file to send
	 * @throws ResponseError
	 *             if the server returned an error response
	 * @throws ParseException
	 *             if an error occurs while parsing the server response
	 * @throws IOException
	 *             if an I/O exception occurs
	 */
	public void sendDocument(long chatId, String path) throws IOException, ParseException, ResponseError {
		sendDocument(chatId, path, null, null);
	}

	/**
	 * Sends a general file to the specified chat.
	 * <p>
	 * Optional parameters can be set to {@code null} for default behaviors.
	 * <p>
	 * Refer to: https://core.telegram.org/bots/api#senddocument
	 * 
	 * @param chatId
	 *            Unique identifier for the target chat
	 * @param path
	 *            Path of the file to send
	 * @param replyToMessageId
	 *            If the message is a reply, ID of the original message
	 * @param replyMarkup
	 *            Map representing the 'reply_markup' parameter. See
	 *            {@link #sendMessage(long, String, String, Boolean, Long, Map)} for
	 *            details
	 * @throws ResponseError
	 *             if the server returned an error response
	 * @throws ParseException
	 *             if an error occurs while parsing the server response
	 * @throws IOException
	 *             if an I/O exception occurs
	 */
	public void sendDocument(long chatId, String path, Long replyToMessageId, Map<String, Object> replyMarkup)
			throws IOException, ParseException, ResponseError {
		log.debug("Sending document to {}: {}", chatId, path);
		sendRequest(new SendDocumentRequest(chatId, Paths.get(path), replyToMessageId, toReplyMarkup(replyMarkup)));
	}

}
