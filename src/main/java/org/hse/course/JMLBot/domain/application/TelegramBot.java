package org.hse.course.JMLBot.domain.application;

import lombok.extern.slf4j.Slf4j;
import org.hse.course.JMLBot.application.datasource.BotConfig;
import org.hse.course.JMLBot.domain.model.Pun;
import org.hse.course.JMLBot.domain.model.PunRepository;
import org.hse.course.JMLBot.domain.model.User;
import org.hse.course.JMLBot.domain.model.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PunRepository punRepository;

    final BotConfig config;
    static final String YES_BUTTON = "YES_BUTTON";
    static final String NO_BUTTON = "NO_BUTTON";

    static final String HELP_TEXT = """
            Данный бот демонстрирует возможности SpringBoot
                        
            Можно выбрать команду в меню слева или написав её
                        
            /start показывает приветственное сообщение
            /pun покажет новый каламбур
            /help покажет данное сообщение
            """;

    static final String UNKNOWN_TEXT = "Данная команда не поддерживается";
    static final String SUCCESS_ADDING_TEXT = "Каламбур добавлен";
    static final String UNSUCCESS_ADDING_TEXT = "Каламбур НЕ добавлен";
    static final String ERROR_TEXT = "Произошла ошибка: ";
    static String PUN_TO_ADD;

    public TelegramBot(BotConfig config) {

        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "приветствие пользователя"));
        listOfCommands.add(new BotCommand("/pun", "новый каламбур"));
        listOfCommands.add(new BotCommand("/help", "информация об использовании"));

        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Ошибка при настройке списка команд бота: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().hasText()) {

            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.contains("/addpun") && config.getOwnerId() == chatId) {

                PUN_TO_ADD = messageText.substring(messageText.indexOf(" ")).trim();
                printAlarmMessage(chatId, PUN_TO_ADD);

            } else {

                switch (messageText) {

                    case "/start":
                        registerUser(update.getMessage());
                        startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                        break;

                    case "/pun":
                        printPun(chatId);
                        break;

                    case "/help":
                        sendMessage(chatId, HELP_TEXT);
                        break;

                    default:
                        sendMessage(chatId, UNKNOWN_TEXT);
                        break;
                }
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.equals(YES_BUTTON)) {
                addPun(PUN_TO_ADD);
                executeEditMessageText(SUCCESS_ADDING_TEXT, chatId, messageId);

            } else if (callbackData.equals(NO_BUTTON)) {
                executeEditMessageText(UNSUCCESS_ADDING_TEXT, chatId, messageId);

            }
        }
    }

    private void startCommandReceived(long chatId, String name) {

        String answer = "Привет, " + name + ". Добро пожаловать в бот с каламбурами!";
        log.info("Ответ пользователю " + name);

        sendMessage(chatId, answer);

    }

    private void registerUser(Message message) {

        if (userRepository.findById(message.getChatId()).isEmpty()) {
            Long chatId = message.getChatId();
            Chat chat = message.getChat();

            User user = new User();
            user.setChatId(chatId);
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

            userRepository.save(user);
            log.info("Пользователь сохранён: " + user);
        }
    }

    public void addPun(String text) {
        Pun pun = new Pun();
        pun.setText(text);
        punRepository.save(pun);
    }

    public void printAlarmMessage(long chatId, String punToAdd) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Действительно опубликовать текст?\n\"\n" + punToAdd + "\n\"");

        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttonsRows = new ArrayList<>();
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        InlineKeyboardButton yesButton = new InlineKeyboardButton();
        yesButton.setText("Да!");
        yesButton.setCallbackData(YES_BUTTON);

        InlineKeyboardButton noButton = new InlineKeyboardButton();
        noButton.setText("Нет");
        noButton.setCallbackData(NO_BUTTON);

        buttons.add(yesButton);
        buttons.add(noButton);

        buttonsRows.add(buttons);

        markupInLine.setKeyboard(buttonsRows);
        message.setReplyMarkup(markupInLine);

        executeMessage(message);
    }

    public void printPun(long chatId) {

        Random random = new Random();
        Long randomId = random.nextLong(punRepository.findMaxId() - 1) + 1;
        Optional<Pun> punById = punRepository.findById(randomId);
        punById.ifPresent(randomPun -> sendMessage(chatId, randomPun.getText()));
    }

    private void sendMessage(long chatId, String textToSend) {

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);

        executeMessage(message);
    }

    private void executeMessage(SendMessage message) {

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void executeEditMessageText(String text, long chatId, int messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setText(text);
        message.setMessageId(messageId);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }
}
