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
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
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

    static final String HELP_TEXT = """
            Данный бот демонстрирует возможности SpringBoot
                        
            Можно выбрать команду в меню слева или написав её
                        
            /start показывает приветственное сообщение
            /printpun покажет новый каламбур
            /help покажет данное сообщение
            """;

    static final String UNKNOWN_TEXT = "Данная команда не поддерживается";
    static final String SUCCESS_ADDING_TEXT = "Каламбур добавлен";

    public TelegramBot(BotConfig config) {

        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "приветствие пользователя"));
        listOfCommands.add(new BotCommand("/printpun", "новый каламбур"));
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

                String textToAdd = messageText.substring(messageText.indexOf(" ")).trim();
                addPun(textToAdd);
                sendMessage(chatId, SUCCESS_ADDING_TEXT);

            } else {

                switch (messageText) {

                    case "/start":
                        registerUser(update.getMessage());
                        startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                        break;

                    case "/printpun":
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

        }
    }

    private void startCommandReceived(long chatId, String name) {

        String answer = "Привет, " + name + ", добро пожаловать!";
        log.info("Ответ пользователю " + name);

        sendMessage(chatId, answer);

    }

    private void registerUser(Message message) {

        if (userRepository.findById(message.getChatId()).isEmpty()) {
            Long chatId = message.getChatId();
            Chat chat = message.getChat();

            User user = new User();
            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
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
            log.error("Произошла ошибка: " + e.getMessage());
        }
    }
}
