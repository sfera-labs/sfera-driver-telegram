package cc.sferalabs.sfera.drivers.telegram;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.simple.parser.ParseException;

import cc.sferalabs.libs.telegram.bot.api.ResponseError;
import cc.sferalabs.libs.telegram.bot.api.TelegramBot;
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
 *
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public class SferaBot extends Driver {

	private static final int POLLING_TIMEOUT = 60000;
	private static final int REQUEST_TIMEOUT = 10000;
	private final Path authorizedUsersFile = getDriverInstanceDataDir().resolve("users");
	private TelegramBot telegram;
	private Integer offset = null;
	private String botSecret;
	private final Set<Integer> authorizedUsers = new HashSet<>();

	public SferaBot(String id) {
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
		if (botSecret == null) {
			log.error("Parameter 'secret' not found in configuration");
			return false;
		}
		telegram = new TelegramBot(token);
		try {
			String botName = telegram.getBotName(5000);
			log.info("Connected to bot: " + botName);
		} catch (IOException | ParseException | ResponseError e) {
			log.error("Error reaching Telegram service", e);
			return false;
		}

		try {
			List<String> lines = Files.readAllLines(authorizedUsersFile);
			for (String line : lines) {
				if (!line.isEmpty()) {
					authorizedUsers.add(Integer.parseInt(line));
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
				
				int updateId = update.getUpdateId();
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
		String text = message.getText();
		User user = message.getFrom();
		int userId = user.getId();

		log.debug("Message from {}: {}", userId, text);

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

		if (authorizedUsers.contains(userId)) {
			Bus.post(new TelegramMessageEvent(this, message));
		} else {
			log.warn("Message from unauthorized user {}: {}", userId, text);
		}
	}

	/**
	 * 
	 * @param id
	 * @param name
	 * @throws IOException
	 */
	private synchronized void addAuthorizedUser(int id, String name) throws IOException {
		authorizedUsers.add(id);

		String users = String.join("\n", authorizedUsers.stream().map(i -> String.valueOf(i))
				.collect(Collectors.<String> toList()));
		if (!Files.exists(authorizedUsersFile)) {
			Files.createDirectories(authorizedUsersFile.getParent());
		}
		try (BufferedWriter writer = Files.newBufferedWriter(authorizedUsersFile)) {
			writer.write(users);
		}

		log.info("Added authorized user: {} ({})", id, name);
	}

	@Override
	protected void onQuit() {
	}

	/**
	 * 
	 * @param chatId
	 * @param text
	 * @throws ResponseError
	 * @throws ParseException
	 * @throws IOException
	 */
	public void sendMessage(int chatId, String text)
			throws IOException, ParseException, ResponseError {
		sendMessage(chatId, text, null, null, null, (ReplyMarkup) null);
	}

	/**
	 * 
	 * @param chatId
	 * @param text
	 * @param parseMode
	 * @param disableWebPagePreview
	 * @param replyToMessageId
	 * @param keyboard
	 * @param resizeKeyboard
	 * @param oneTimeKeyboard
	 * @param selective
	 * @throws IOException
	 * @throws ParseException
	 * @throws ResponseError
	 */
	public void sendMessage(int chatId, String text, String parseMode,
			Boolean disableWebPagePreview, Integer replyToMessageId, String[][] keyboard,
			Boolean resizeKeyboard, Boolean oneTimeKeyboard, Boolean selective)
					throws IOException, ParseException, ResponseError {
		sendMessage(chatId, text, parseMode, disableWebPagePreview, replyToMessageId,
				new ReplyKeyboardMarkup(keyboard, resizeKeyboard, oneTimeKeyboard, selective));
	}

	/**
	 * @param chatId
	 * @param text
	 * @param parseMode
	 * @param disableWebPagePreview
	 * @param replyToMessageId
	 * @param hideOrForce
	 * @param selective
	 * @throws IOException
	 * @throws ParseException
	 * @throws ResponseError
	 */
	public void sendMessage(int chatId, String text, String parseMode,
			Boolean disableWebPagePreview, Integer replyToMessageId, String hideOrForce,
			Boolean selective) throws IOException, ParseException, ResponseError {
		ReplyMarkup replyMarkup;
		if (hideOrForce.toLowerCase().startsWith("hide")) {
			replyMarkup = new ReplyKeyboardHide(selective);
		} else {
			replyMarkup = new ForceReply(selective);
		}
		sendMessage(chatId, text, parseMode, disableWebPagePreview, replyToMessageId, replyMarkup);
	}

	/**
	 * 
	 * @param chatId
	 * @param text
	 * @param parseMode
	 * @param disableWebPagePreview
	 * @param replyToMessageId
	 * @param replyMarkup
	 * @throws ResponseError
	 * @throws ParseException
	 * @throws IOException
	 */
	public void sendMessage(int chatId, String text, String parseMode,
			Boolean disableWebPagePreview, Integer replyToMessageId, ReplyMarkup replyMarkup)
					throws IOException, ParseException, ResponseError {
		log.debug("Sending message to {}: {}", chatId, text);
		telegram.sendRequest(new SendMessageRequest(chatId, text, parseMode, disableWebPagePreview,
				replyToMessageId, replyMarkup), REQUEST_TIMEOUT);
	}

	/**
	 * 
	 * @param chatId
	 * @param action
	 * @throws IOException
	 * @throws ParseException
	 * @throws ResponseError
	 */
	public void sendChatAction(int chatId, String action)
			throws IOException, ParseException, ResponseError {
		log.debug("Sending chat action to {}: {}", chatId, action);
		telegram.sendRequest(new SendChatActionRequest(chatId, action), REQUEST_TIMEOUT);
	}

	/**
	 * 
	 * @param chatId
	 * @param path
	 * @param caption
	 * @throws IOException
	 * @throws ParseException
	 * @throws ResponseError
	 */
	public void sendPhoto(int chatId, String path, String caption)
			throws IOException, ParseException, ResponseError {
		sendPhoto(chatId, path, caption, null, null);
	}

	/**
	 * 
	 * @param chatId
	 * @param path
	 * @param caption
	 * @param replyToMessageId
	 * @param replyMarkup
	 * @throws IOException
	 * @throws ParseException
	 * @throws ResponseError
	 */
	public void sendPhoto(int chatId, String path, String caption, Integer replyToMessageId,
			ReplyMarkup replyMarkup) throws IOException, ParseException, ResponseError {
		log.debug("Sending image to {}: {}", chatId, path);
		telegram.sendRequest(new SendPhotoRequest(chatId, Paths.get(path), caption,
				replyToMessageId, replyMarkup), REQUEST_TIMEOUT);
	}

	/**
	 * 
	 * @param chatId
	 * @param path
	 * @throws IOException
	 * @throws ParseException
	 * @throws ResponseError
	 */
	public void sendDocument(int chatId, String path)
			throws IOException, ParseException, ResponseError {
		sendDocument(chatId, path, null, null);
	}

	/**
	 * 
	 * @param chatId
	 * @param path
	 * @param replyToMessageId
	 * @param replyMarkup
	 * @throws IOException
	 * @throws ParseException
	 * @throws ResponseError
	 */
	public void sendDocument(int chatId, String path, Integer replyToMessageId,
			ReplyMarkup replyMarkup) throws IOException, ParseException, ResponseError {
		log.debug("Sending document to {}: {}", chatId, path);
		telegram.sendRequest(
				new SendDocumentRequest(chatId, Paths.get(path), replyToMessageId, replyMarkup),
				REQUEST_TIMEOUT);
	}

	/**
	 * 
	 * @param chatId
	 * @param path
	 * @param title
	 * @throws IOException
	 * @throws ParseException
	 * @throws ResponseError
	 */
	public void sendAudio(int chatId, String path, String title)
			throws IOException, ParseException, ResponseError {
		sendAudio(chatId, path, null, null, title, null, null);
	}

	/**
	 * 
	 * @param chatId
	 * @param path
	 * @param duration
	 * @param performer
	 * @param title
	 * @param replyToMessageId
	 * @param replyMarkup
	 * @throws IOException
	 * @throws ParseException
	 * @throws ResponseError
	 */
	public void sendAudio(int chatId, String path, Integer duration, String performer, String title,
			Integer replyToMessageId, ReplyMarkup replyMarkup)
					throws IOException, ParseException, ResponseError {
		log.debug("Sending audio to {}: {}", chatId, path);
		telegram.sendRequest(new SendAudioRequest(chatId, Paths.get(path), duration, performer,
				title, replyToMessageId, replyMarkup), REQUEST_TIMEOUT);
	}
}
