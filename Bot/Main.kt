import org.json.JSONArray
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import java.io.File

data class QuizQuestion(val question: String, val answers: List<String>, val correctAnswerIndex: Int)

class SimpleBot : TelegramLongPollingBot() {
    override fun getBotUsername(): String = "Formula1CalendarBot"
    override fun getBotToken(): String = "7584499973:AAFL0Vl2qtHlTLr0EUe6dk98WJ2JbGIzi54"

    private val quizQuestions = listOf(
        QuizQuestion("Кто выиграл чемпионат мира Формулы 1 в 2020 году?", listOf("Льюис Хэмилтон", "Валттери Боттас", "Макс Ферстаппен", "Себастьян Феттель"), 0),
        QuizQuestion("Какой автомобиль был первым в истории Формулы 1?", listOf("Mercedes W196", "Ferrari 125 F1", "Lotus 49", "McLaren MP4/4"), 1),
        QuizQuestion("Какой гонщик имеет наибольшее количество побед в Формуле 1?", listOf("Льюис Хэмилтон", "Михаэль Шумахер", "Ален Прост", "Себастьян Феттель"), 0),
        QuizQuestion("Какой год считается годом основания Формулы 1?", listOf("1946", "1950", "1965", "1970"), 1),
        QuizQuestion("Какой гонщик выиграл чемпионат мира в 1994 году?", listOf("Михаэль Шумахер", "Ален Прост", "Деймон Хилл", "Нельсон Пике"), 0),
        QuizQuestion("Какой из этих команд не существует в Формуле 1?", listOf("Ferrari", "Mercedes", "Renault", "Porsche"), 3),
        QuizQuestion("Какой гонщик стал чемпионом мира в 2005 и 2006 годах?", listOf("Фернандо Алонсо", "Льюис Хэмилтон", "Кими Райкконен", "Михаэль Шумахер"), 0),
        QuizQuestion("Какой из этих автодромов не используется в Формуле 1?", listOf("Сильверстоун", "Монако", "Лагос", "Сузука"), 2),
        QuizQuestion("Кто является самым молодым чемпионом мира в истории Формулы 1?", listOf("Льюис Хэмилтон", "Кими Райкконен", "Макс Ферстаппен", "Фернандо Алонсо"), 2),
        QuizQuestion("Какой из этих гонщиков не выступал за команду Red Bull Racing?", listOf("Себастьян Феттель", "Даниэль Риккардо", "Макс Ферстаппен", "Льюис Хэмилтон"), 3)
    )
    private var currentQuestionIndex = -1
    private val userAnswers = mutableMapOf<Long, MutableList<Int>>()

    override fun onUpdateReceived(update: Update?) {
        if (update != null && update.hasMessage() && update.message.hasText()) {
            val messageText = update.message.text
            val chatId = update.message.chatId

            when (messageText) {
                "Календарь" -> sendCalendarImage(chatId)
                "Турнирная таблица Кубка конструкторов" -> sendResponse(chatId, getConstructorsStandings(), showMainButtons())
                "Информация о машинах 2024" -> sendResponse(chatId, "Выберите команду:", showCarsButtons())
                "Турнирная таблица пилотов" -> sendPilotsStandings(chatId)
                "Трассы" -> sendTrackButtons(chatId)
                "Викторина" -> startQuiz(chatId)
                "Завершить викторину" -> {
                    if (currentQuestionIndex != -1) {
                        endQuiz(chatId)
                    } else {
                        sendResponse(chatId, "Викторина еще не началась.", showMainButtons())
                    }
                }
                else -> {
                    val (response, imageUrl) = processCommand(messageText)
                    sendResponse(chatId, response, showMainButtons())
                    if (imageUrl.isNotBlank()) sendCarPhoto(chatId, imageUrl)
                }
            }
        } else if (update != null && update.hasCallbackQuery()) {
            val callbackData = update.callbackQuery.data
            val chatId = update.callbackQuery.message.chatId
            handleAnswer(chatId, callbackData.toInt())
        }
    }


    private fun sendPilotsStandings(chatId: Long) {
        val response = getPilotsStandings()
        sendResponse(chatId, response, showMainButtons())
    }

    private fun getPilotsStandings(): String {
        return try {
            val jsonString = File("table.json").readText(Charsets.UTF_8)
            val teamsArray = JSONArray(jsonString)

            val standings = StringBuilder("Турнирная таблица пилотов:\n\n")
            standings.append("║════════════════════════════════════║\n\n")

            for (i in 0 until teamsArray.length()) {
                val team = teamsArray.getJSONObject(i)
                standings.append(
                    "Пилот:  ${team.getString("Имя")}, " +
                            "Поб: ${team.getString("Название команды")}, " +
                            "ПЛ: ${team.getString("ПОБ")}, " +
                            "ЛК: ${team.getString("ПЛ")}, " +
                            "ЛК: ${team.getString("ЛК")}, " +
                            "Очки: ${team.getString("Очки")}\n\n"
                )
            }
            standings.append("║════════════════════════════════════║\n\n")
            standings.append("Примечание: Поб - количество побед, ПЛ - количество поул-позиций, ЛК - количество лучших кругов.\n")
            standings.toString()
        } catch (e: Exception) {
            "Не удалось загрузить турнирную таблицу"
        }
    }


    private fun startQuiz(chatId: Long) {
        currentQuestionIndex = 0
        userAnswers[chatId] = mutableListOf()
        sendQuestion(chatId)
    }

    private fun endQuiz(chatId: Long) {
        if (currentQuestionIndex != -1) {
            sendResponse(chatId, "Викторина завершена!", showMainButtons())
            currentQuestionIndex = -1
            userAnswers.remove(chatId)
        } else {
            sendResponse(chatId, "Викторина еще не началась.", showMainButtons()) // Если викторина не начата
        }
    }
    private fun sendQuestion(chatId: Long) {
        if (currentQuestionIndex < quizQuestions.size) {
            val question = quizQuestions[currentQuestionIndex]
            val markup = InlineKeyboardMarkup()
            val buttons = question.answers.mapIndexed { index, answer ->
                InlineKeyboardButton(answer).apply { callbackData = index.toString() }
            }
            markup.keyboard = listOf(buttons)

            val message = SendMessage().apply {
                this.chatId = chatId.toString()
                text = question.question
                replyMarkup = markup
            }
            try { execute(message) } catch (e: TelegramApiException) { e.printStackTrace() }
        } else {
            sendResults(chatId)
        }
    }

    private fun handleAnswer(chatId: Long, answerIndex: Int) {
        userAnswers[chatId]?.add(answerIndex)
        currentQuestionIndex++
        sendQuestion(chatId)
    }

    private fun sendResults(chatId: Long) {
        val answers = userAnswers[chatId] ?: return
        val correctAnswersCount = answers.withIndex().count { (index, answer) ->
            answer == quizQuestions[index].correctAnswerIndex
        }
        val resultMessage = "Ваши результаты:\nВы ответили на ${answers.size} вопросов.\nПравильных ответов: $correctAnswersCount из ${quizQuestions.size}."
        sendResponse(chatId, resultMessage, showMainButtons())
    }

    private fun sendResponse(chatId: Long, response: String, replyMarkup: ReplyKeyboardMarkup?) {
        val message = SendMessage().apply {
            this.chatId = chatId.toString()
            this.text = response
            this.replyMarkup = replyMarkup
        }
        try { execute(message) } catch (e: TelegramApiException) { e.printStackTrace() }
    }

    private fun processCommand(messageText: String): Pair<String, String> {
        return when (messageText) {
            "Календарь" -> Pair("Календарь Формулы 1 отправлен.", "")
            "Турнирная таблица Кубка конструкторов" -> Pair(getConstructorsStandings(), "")
            "Oracle Red Bull Racing" -> Pair("Информация о Oracle Red Bull Racing\nБолид: Oracle Red Bull Racing - RB20\nМотор: Honda RBPT", "https://cdn.f1ne.ws/userfiles/RB20-2024.jpg")
            "Mercedes-AMG PETRONAS Formula One Team" -> Pair("Информация о Mercedes-AMG PETRONAS\nБолид: Mercedes AMG W15\nМотор: Mercedes", "https://cdn.f1ne.ws/userfiles/W15-2024.jpg")
            "Scuderia Ferrari" -> Pair("Информация о Scuderia Ferrari\nБолид: Ferrari SF-24\nМотор: Ferrari", "https://cdn.f1ne.ws/userfiles/SF-24.jpg")
            "McLaren Formula 1 Team" -> Pair("Информация о McLaren\nБолид: McLaren MCL38\nМотор:Мотор: Mercedes", "https://cdn.f1ne.ws/userfiles/MCL38-2024.jpg")
            "Aston Martin Aramco Formula One Team" -> Pair("Информация о Aston Martin\nБолид:Aston Martin AMR24\nМотор: Mercedes", "https://cdn.f1ne.ws/userfiles/AMR24-1.jpg")
            "BWT Alpine F1 Team" -> Pair("Информация о BWT Alpine\nМашина: Alpine A524\nМотор: Renault", "https://cdn.f1ne.ws/userfiles/A524.jpg")
            "Williams Racing" -> Pair("Информация о Williams Racing\nМашина: Williams Racing - FW46\nМотор: Ferrari", "https://cdn.f1ne.ws/userfiles/FW46.jpg")
            "Visa Cash App RB Formula One Team" -> Pair("Информация о Visa Cash App RB Formula One Team\nБолид: Visa Cash App RB Formula One Team - VCARB 01\nМотор: Honda RBPT", "https://cdn.f1ne.ws/userfiles/VCARB-01.jpg")
            "Stake F1 Team Kick Sauber" -> Pair("Информация о Stake F1 Team Kick Sauber - Kick Sauber\nБолид:Kick Sauber\nМотор: Ferrari", "https://cdn.f1ne.ws/userfiles/sauber-2024.jpg")
            "MoneyGram Haas F1 Team" -> Pair("Информация о MoneyGram Haas F1 Team\nБолид: VF-24\n Мотор: Ferrari ", "https://cdn.f1ne.ws/userfiles/VF-24.jpg")
            "Назад в главное меню" -> Pair("Главное меню", "")
            else -> Pair("Неизвестная команда", "")
        }
    }

    private fun sendCarPhoto(chatId: Long, imageUrl: String) {
        if (imageUrl.isBlank()) return
        val sendPhoto = SendPhoto(chatId.toString(), InputFile(imageUrl)).apply { caption = "Фотография автомобиля команды" }
        try { execute(sendPhoto) } catch (e: TelegramApiException) { e.printStackTrace() }
    }

    private fun showCarsButtons(): ReplyKeyboardMarkup {
        val keyboardMarkup = ReplyKeyboardMarkup().apply { resizeKeyboard = true }
        val buttons = ArrayList<KeyboardRow>()

        val row1 = KeyboardRow().apply {
            add("Oracle Red Bull Racing")
            add("Mercedes-AMG PETRONAS Formula One Team")
        }
        val row2 = KeyboardRow().apply {
            add("Scuderia Ferrari")
            add("McLaren Formula 1 Team")
        }
        val row3 = KeyboardRow().apply {
            add("Aston Martin Aramco Formula One Team")
            add("BWT Alpine F1 Team")
        }
        val row4 = KeyboardRow().apply {
            add("Williams Racing")
            add("Visa Cash App RB Formula One Team")
        }
        val row5 = KeyboardRow().apply {
            add("Stake F1 Team Kick Sauber")
            add("MoneyGram Haas F1 Team")
        }
        val row6 = KeyboardRow().apply { add("Назад в главное меню") }

        buttons.addAll(listOf(row1, row2, row3, row4, row5, row6))
        keyboardMarkup.keyboard = buttons
        return keyboardMarkup
    }

    private fun showMainButtons(): ReplyKeyboardMarkup {
        val keyboardMarkup = ReplyKeyboardMarkup().apply { resizeKeyboard = true }
        val buttons = ArrayList<KeyboardRow>()

        val row1 = KeyboardRow().apply {
            add("Календарь")
            add("Турнирная таблица Кубка конструкторов")
        }
        val row2 = KeyboardRow().apply { add("Информация о машинах 2024") }
        val row3 = KeyboardRow().apply {
            add("Викторина")
            add("Завершить викторину")
        }
        val row4 = KeyboardRow().apply {
            add("Трассы")
            add("Турнирная таблица пилотов")
        }

        buttons.addAll(listOf(row1, row2, row3, row4))
        keyboardMarkup.keyboard = buttons
        return keyboardMarkup
    }
    private fun sendTrackButtons(chatId: Long) {
        val inlineKeyboardMarkup = InlineKeyboardMarkup()

        val tracks = listOf(
            "Бахрейн, Сахир",
            "Саудовская Аравия, Джидда",
            "Австралия, Альберт-Парк",
            "Япония, Сузука",
            "Китай, Шанхай",
            "США, Майами",
            "Италия, Имола",
            "Монако, Монте-Карло",
            "Канада, Монреаль",
            "Испания, Барселона-Каталунья",
            "Австрия, Ред Булл Ринг",
            "Британия, Сильверстоун",
            "Венгрия, Хунгароринг",
            "Бельгия, Спа-Франкоршам",
            "Нидерланды, Зандфорт",
            "Италия, Монца",
            "Азербайджан, Баку",
            "Сингапур, Марина Бей",
            "США, Америк",
            "Мексика, Мехико-Сити",
            "Бразилия, Интерлагос",
            "США, Лас-Вегас",
            "Катар, Лосаил",
            "Абу-Даби, Яс Марина"
        )

        val buttons = tracks.map { track ->
            InlineKeyboardButton(track).apply {
                callbackData = track
            }
        }

        inlineKeyboardMarkup.keyboard = buttons.chunked(2).map { it.toMutableList() }

        val message = SendMessage().apply {
            this.chatId = chatId.toString()
            text = "Выберите трассу:"
            replyMarkup = inlineKeyboardMarkup
        }

        try { execute(message) } catch (e: TelegramApiException) { e.printStackTrace() }
    }


    private fun getConstructorsStandings(): String {
        return try {
            val jsonString = File("teams.json").readText(Charsets.UTF_8)
            val teamsArray = JSONArray(jsonString)

            val standings = StringBuilder("Турнирная таблица Кубка конструкторов:\n\n")
            standings.append("║════════════════════════════════════║\n\n")

            for (i in 0 until teamsArray.length()) {
                val team = teamsArray.getJSONObject(i)
                standings.append(
                    "Команда:  ${team.getString("Команда")}, " +
                            "Поб: ${team.getString("Поб")}, " +
                            "ПЛ: ${team.getString("ПЛ")}, " +
                            "ЛК: ${team.getString("ЛК")}, " +
                            "Очки: ${team.getString("Очки")}\n\n"
                )
            }
            standings.append("║════════════════════════════════════║\n\n")
            standings.append("Примечание: Поб - количество побед, ПЛ - количество поул-позиций, ЛК - количество лучших кругов.\n")
            standings.toString()
        } catch (e: Exception) {
            "Не удалось загрузить таблицу"
        }
    }

    private fun sendCalendarImage(chatId: Long) {
        val imageUrl = "https://f-1world.ru/posters/f1-2024-calendar.webp"
        if (imageUrl.isNotBlank()) {
            val sendPhoto = SendPhoto(chatId.toString(), InputFile(imageUrl)).apply { caption = "Календарь Формулы 1 на 2024 год" }
            try { execute(sendPhoto) } catch (e: TelegramApiException) { e.printStackTrace() }
        } else {
            sendResponse(chatId, "Ошибка загрузки календаря", showMainButtons())
        }
    }
}

fun main() {
    val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
    try {
        botsApi.registerBot(SimpleBot())
        println("Бот запущен!")
    } catch (e: TelegramApiException) {
        e.printStackTrace()
    }
}
