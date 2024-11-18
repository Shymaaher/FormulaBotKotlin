import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

class SimpleBot : TelegramLongPollingBot() {

    override fun getBotUsername(): String {
        return "Formula1CalendarBot"
    }

    override fun getBotToken(): String {
        return "7584499973:AAFL0Vl2qtHlTLr0EUe6dk98WJ2JbGIzi54"
    }

    override fun onUpdateReceived(update: Update?) {
        if (update != null && update.hasMessage() && update.message.hasText()) {
            val messageText = update.message.text
            val chatId = update.message.chatId

            val responseMessage = SendMessage()
            responseMessage.chatId = chatId.toString()
            responseMessage.text = "Привет!"

            try {
                execute(responseMessage)
            } catch (e: TelegramApiException) {
                e.printStackTrace()
            }
        }
    }
}

fun main() {
    val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
    try {
        botsApi.registerBot(SimpleBot())  // Регистрируем бота
        println("Бот запущен!")
    } catch (e: TelegramApiException) {
        e.printStackTrace()
    }
}