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
/**
 * Бот для Telegram, реализующий функционал викторины, отправки календаря Формулы 1,
 * турнирных таблиц, информации о командах и трассах.
 */
class SimpleBot : TelegramLongPollingBot() {
    /**
     * Возвращает имя бота.
     * @return Имя бота.
     */
    override fun getBotUsername(): String = "Formula1CalendarBot"
    /**
     * Возвращает токен бота.
     * @return Токен бота.
     */
    override fun getBotToken(): String = "7584499973:AAFL0Vl2qtHlTLr0EUe6dk98WJ2JbGIzi54"
    /**
     * Класс, представляющий вопрос викторины.
     *
     * @property question Вопрос викторины.
     * @property answers Список вариантов ответов на вопрос.
     * @property correctAnswerIndex Индекс правильного ответа в списке вариантов.
     */
    private val quizQuestions = listOf(
        /**
         * Список вопросов викторины.
         */
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
    /**
     * Обрабатывает обновления, полученные от пользователей.
     *
     * @param update Обновление, содержащее информацию о сообщении или обратном вызове.
     */
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
                    // Обрабатываем команду и отправляем ответ
                    val (response, imageUrl) = processCommand(messageText)
                    sendResponse(chatId, response, showMainButtons())

                    // Если URL изображения не пустой, отправляем фото
                    if (imageUrl.isNotBlank()) {
                        sendCarPhoto(chatId, imageUrl)
                    }
                }
            }
        } else if (update != null && update.hasCallbackQuery()) {
            val callbackData = update.callbackQuery.data
            val chatId = update.callbackQuery.message.chatId

            if (trackPhotos.containsKey(callbackData)) {
                val imageUrl = trackPhotos[callbackData]
                if (imageUrl != null) {
                    sendTrackPhoto(chatId, imageUrl, callbackData)
                }
            } else {
                handleAnswer(chatId, callbackData.toIntOrNull() ?: -1)
            }
        }
    }

    /**
     * Отправляет турнирную таблицу пилотов.
     *
     * @param chatId Идентификатор чата, куда будет отправлено сообщение.
     */
    private fun sendPilotsStandings(chatId: Long) {
        val response = getPilotsStandings()
        sendResponse(chatId, response, showMainButtons())
    }
    /**
     * Загружает турнирную таблицу пилотов из файла JSON.
     *
     * @return Строка с турнирной таблицей пилотов.
     */
    private fun getPilotsStandings(): String {
        return try {
            val jsonString = File("table.json").readText(Charsets.UTF_8)
            val teamsArray = JSONArray(jsonString)

            val standings = StringBuilder("Турнирная таблица пилотов:\n\n")
            standings.append("║════════════════════════════════════║\n\n")

            for (i in 0 until teamsArray.length()) {
                val team = teamsArray.getJSONObject(i)
                standings.append(
                    "Команда:  ${team.getString("Имя")}, " +
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

    /**
     * Начинает викторину.
     *
     * @param chatId Идентификатор чата, в котором начинается викторина.
     */
    private fun startQuiz(chatId: Long) {
        currentQuestionIndex = 0
        userAnswers[chatId] = mutableListOf()
        sendQuestion(chatId)
    }
    /**
     * Завершает викторину и отображает результаты.
     *
     * @param chatId Идентификатор чата, в котором завершена викторина.
     */
    private fun endQuiz(chatId: Long) {
        if (currentQuestionIndex != -1) {
            sendResponse(chatId, "Викторина завершена!", showMainButtons())
            currentQuestionIndex = -1
            userAnswers.remove(chatId)
        } else {
            sendResponse(chatId, "Викторина еще не началась.", showMainButtons()) // Если викторина не начата
        }
    }
    /**
     * Отправляет следующий вопрос викторины.
     *
     * @param chatId Идентификатор чата, в котором будет отправлен следующий вопрос.
     */
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
    /**
     * Обрабатывает выбор ответа пользователя.
     *
     * @param chatId Идентификатор чата, в котором пользователь выбрал ответ.
     * @param answerIndex Индекс выбранного ответа.
     */
    private fun handleAnswer(chatId: Long, answerIndex: Int) {
        userAnswers[chatId]?.add(answerIndex)
        currentQuestionIndex++
        sendQuestion(chatId)
    }
    // Методы для отправки сообщений и изображений
    private fun sendResults(chatId: Long) {
        val answers = userAnswers[chatId] ?: return
        val correctAnswersCount = answers.withIndex().count { (index, answer) ->
            answer == quizQuestions[index].correctAnswerIndex
        }
        val resultMessage = "Ваши результаты:\nВы ответили на ${answers.size} вопросов.\nПравильных ответов: $correctAnswersCount из ${quizQuestions.size}."
        sendResponse(chatId, resultMessage, showMainButtons())
    }
    /**
     * Отправляет сообщение с клавиатурой.
     *
     * @param chatId Идентификатор чата.
     * @param response Текст сообщения.
     * @param keyboard Клавиатура для ответа.
     */
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
    /**
     * Отправляет фото о машине.
     *
     * @param chatId Идентификатор чата.
     * @param imageUrl URL изображения.
     */
    private fun sendCarPhoto(chatId: Long, imageUrl: String) {
        if (imageUrl.isBlank()) return
        val sendPhoto = SendPhoto(chatId.toString(), InputFile(imageUrl)).apply { caption = "Фотография автомобиля команды" }
        try { execute(sendPhoto) } catch (e: TelegramApiException) { e.printStackTrace() }
    }
    /**
     * Создает клавиатуру для выбора команды Формулы 1.
     * Клавиатура содержит кнопки для разных команд и одну кнопку для возвращения в главное меню.
     *
     * @return Объект [ReplyKeyboardMarkup], представляющий клавиатуру с кнопками команд.
     *         Каждая кнопка представляет собой команду Формулы 1, а последняя кнопка
     *         позволяет вернуться в главное меню.
     */
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
    /**
     * Показывает основные кнопки меню.
     *
     * @return Клавиатура с основными кнопками.
     */
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
    /**
     * Отправляет инлайн-кнопки для выбора трассы.
     * Кнопки содержат список трасс Формулы 1, каждая кнопка при нажатии возвращает название трассы.
     *
     * @param chatId Идентификатор чата, в который будет отправлено сообщение с кнопками.
     */
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
    private val trackPhotos = mapOf(
        "Бахрейн, Сахир" to "https://f1report.ru/img/fotos/2022/03/f1-1647513927.png",
        "Саудовская Аравия, Джидда" to "https://f1report.ru/img/fotos/2021/11/f1-1637845657.png",
        "Австралия, Альберт-Парк" to "https://f1report.ru/img/tracks/albert_park_track_f1_2022.png",
        "Япония, Сузука" to "https://f1report.ru/img/fotos/2023/09/f1-1695289698.jpg",
        "Китай, Шанхай" to "https://f1report.ru/img/fotos/2024/04/f1-1713658076.jpg",
        "США, Майами" to "https://f1report.ru/img/tracks/miami_trassa_f1_2024.png",
        "Италия, Имола" to "https://f1report.ru/img/fotos/2022/04/f1-1650538642.png",
        "Монако, Монте-Карло" to "https://f1report.ru/img/fotos/2024/05/f1-1716503092.png",
        "Канада, Монреаль" to "https://f1report.ru/img/fotos/2023/06/f1-1686840364.jpg",
        "Испания, Барселона-Каталунья" to "https://f1report.ru/img/fotos/2023/06/f1-1685660476.jpg",
        "Австрия, Ред Булл Ринг" to "https://f1report.ru/img/fotos/2023/06/f1-1688078094.jpg",
        "Британия, Сильверстоун" to "https://f1report.ru/img/fotos/2024/07/f1-1720133312.jpg",
        "Венгрия, Хунгароринг" to "https://f1report.ru/img/tracks/hungaroring-shema-trassi-2021.png",
        "Бельгия, Спа-Франкоршам" to "https://f1report.ru/img/tracks/spa-francorchamps_2021/f1-1630019541.png",
        "Нидерланды, Зандфорт" to "https://f1report.ru/img/fotos/2021/09/f1-1630625185.png",
        "Италия, Монца" to "https://f1report.ru/img/tracks/monza_2021.png",
        "Азербайджан, Баку" to "https://f1report.ru/img/fotos/2021/06/f1-1622759639.png",
        "Сингапур, Марина Бей" to "https://f1report.ru/img/fotos/2023/09/f1-1694779564.png",
        "США, Америк" to "https://f1report.ru/img/fotos/2022/10/f1-1666223173.png",
        "Мексика, Мехико-Сити" to "https://f1report.ru/img/tracks/trassa-f1-mexico-city-2021.png",
        "Бразилия, Интерлагос" to "https://f1report.ru/img/tracks/brazil_interlagos_2024.png",
        "США, Лас-Вегас" to "https://f1report.ru/img/fotos/2023/11/f1-1700052155.jpg",
        "Катар, Лосаил" to "https://f1report.ru/img/fotos/2023/10/f1-1696548159.png",
        "Абу-Даби, Яс Марина" to "https://f1report.ru/img/fotos/2021/12/f1-1639132582.png",
    )
    private val trackInfo = mapOf(
        "Бахрейн, Сахир" to "Официальное название трассы: Bahrain International Circuit\nОфициальный сайт: https://www.bahraingp.com/\nДлина: 5.412 км\nКоличество кругов: 57\nПродолжительность гонки: около 1ч 33 мин",
        "Саудовская Аравия, Джидда" to "Официальное название трассы: Jeddah Street Circuit\nОфициальный сайт: https://www.saudiarabiangp.com/\nДлина: 6.174 км\nКоличество кругов: 50\nПродолжительность гонки: около 1ч 31 мин",
        "Австралия, Альберт-Парк" to "Официальное название трассы: Melbourne Grand Prix Circuit\nОфициальный сайт: https://www.grandprix.com.au/\nДлина: 5.278 км\nКоличество кругов: 58\nПродолжительность гонки: около 1ч 30 мин",
        "Япония, Сузука" to "Официальное название трассы: Suzuka Circuit\nОфициальный сайт: https://www.suzukacircuit.jp/\nДлина: 5.807 км\nКоличество кругов: 53\nПродолжительность гонки: около 1ч 30 мин",
        "Китай, Шанхай" to "Официальное название трассы: Shanghai International Circuit\nОфициальный сайт: https://www.shanghaigp.com/\nДлина: 5.451 км\nКоличество кругов: 56\nПродолжительность гонки: около 1ч 35 мин",
        "США, Майами" to "Официальное название трассы: Miami International Autodrome\nОфициальный сайт: https://www.miamigp.com/\nДлина: 5.41 км\nКоличество кругов: 57\nПродолжительность гонки: около 1ч 36 мин",
        "Италия, Имола" to "Официальное название трассы: Autodromo Enzo e Dino Ferrari\nОфициальный сайт: https://www.autodromoimola.it/\nДлина: 4.909 км\nКоличество кругов: 63\nПродолжительность гонки: около 1ч 32 мин",
        "Монако, Монте-Карло" to "Официальное название трассы: Circuit de Monaco\nОфициальный сайт: https://www.formula1.com/en/racing/2024/Monaco.html\nДлина: 3.337 км\nКоличество кругов: 78\nПродолжительность гонки: около 1ч 45 мин",
        "Канада, Монреаль" to "Официальное название трассы: Circuit Gilles Villeneuve\nОфициальный сайт: https://www.circuitgillesvilleneuve.ca/\nДлина: 4.361 км\nКоличество кругов: 70\nПродолжительность гонки: около 1ч 30 мин",
        "Испания, Барселона-Каталунья" to "Официальное название трассы: Circuit de Barcelona-Catalunya\nОфициальный сайт: https://www.circuitcat.com/\nДлина: 4.675 км\nКоличество кругов: 66\nПродолжительность гонки: около 1ч 35 мин",
        "Австрия, Ред Булл Ринг" to "Официальное название трассы: Red Bull Ring\nОфициальный сайт: https://www.projekt-spielberg.com/\nДлина: 4.318 км\nКоличество кругов: 71\nПродолжительность гонки: около 1ч 20 мин",
        "Британия, Сильверстоун" to "Официальное название трассы: Silverstone Circuit\nОфициальный сайт: https://www.silverstone.co.uk/\nДлина: 5.891 км\nКоличество кругов: 52\nПродолжительность гонки: около 1ч 30 мин",
        "Венгрия, Хунгароринг" to "Официальное название трассы: Hungaroring\nОфициальный сайт: https://www.hungaroring.hu/\nДлина: 4.381 км\nКоличество кругов: 70\nПродолжительность гонки: около 1ч 35 мин",
        "Бельгия, Спа-Франкоршам" to "Официальное название трассы: Circuit de Spa-Francorchamps\nОфициальный сайт: https://www.spa-francorchamps.be/\nДлина: 7.004 км\nКоличество кругов: 44\nПродолжительность гонки: около 1ч 45 мин",
        "Нидерланды, Зандфорт" to "Официальное название трассы: Circuit Zandvoort\nОфициальный сайт: https://www.circuitzandvoort.nl/\nДлина: 4.259 км\nКоличество кругов: 72\nПродолжительность гонки: около 1ч 30 мин",
        "Италия, Монца" to "Официальное название трассы: Autodromo Nazionale Monza\nОфициальный сайт: https://www.monzanet.it/\nДлина: 5.793 км\nКоличество кругов: 53\nПродолжительность гонки: около 1ч 15 мин",
        "Азербайджан, Баку" to "Официальное название трассы: Baku City Circuit\nОфициальный сайт: https://www.bakucitycircuit.com/\nДлина: 6.003 км\nКоличество кругов: 51\nПродолжительность гонки: около 1ч 40 мин",
        "Сингапур, Марина Бей" to "Официальное название трассы: Marina Bay Street Circuit\nОфициальный сайт: https://singaporegp.sg/\nДлина: 5.063 км\nКоличество кругов: 61\nПродолжительность гонки: около 1ч 50 мин",
        "США, Америк" to "Официальное название трассы: Circuit of the Americas\nОфициальный сайт: https://www.circuitoftheamericas.com/\nДлина: 5.513 км\nКоличество кругов: 56\nПродолжительность гонки: около 1ч 40 мин",
        "Мексика, Мехико-Сити" to "Официальное название трассы: Autódromo Hermanos Rodríguez\nОфициальный сайт: https://www.autodromorodriguez.com.mx/\nДлина: 4.304 км\nКоличество кругов: 71\nПродолжительность гонки: около 1ч 40 мин",
        "Бразилия, Интерлагос" to "Официальное название трассы: Autódromo José Carlos Pace\nОфициальный сайт: https://www.grandepremio.com.br/\nДлина: 4.309 км\nКоличество кругов: 71\nПродолжительность гонки: около 1ч 30 мин",
        "США, Лас-Вегас" to "Официальное название трассы: Las Vegas Grand Prix\nОфициальный сайт: https://www.f1lasvegasgp.com/\nДлина: 6.120 км\nКоличество кругов: 50\nПродолжительность гонки: около 1ч 40 мин",
        "Катар, Лосаил" to "Официальное название трассы: Losail International Circuit\nОфициальный сайт: https://www.lcsc.qa/\nДлина: 5.380 км\nКоличество кругов: 57\nПродолжительность гонки: около 1ч 30 мин",
        "Абу-Даби, Яс Марина" to "Официальное название трассы: Yas Marina Circuit\nОфициальный сайт: https://www.yasmarinacircuit.com/\nДлина: 5.281 км\nКоличество кругов: 55\nПродолжительность гонки: около 1ч 40 мин",


    )

    private fun sendTrackPhoto(chatId: Long, imageUrl: String, trackName: String) {
        val trackDescription = trackInfo[trackName] ?: "Информация о трассе недоступна."

        val message = SendMessage().apply {
            this.chatId = chatId.toString()
            this.text = trackDescription
        }
        try { execute(message) } catch (e: TelegramApiException) { e.printStackTrace() }

        if (imageUrl.isNotBlank()) {
            val sendPhoto = SendPhoto(chatId.toString(), InputFile(imageUrl)).apply { caption = "Фотография трассы $trackName" }
            try { execute(sendPhoto) } catch (e: TelegramApiException) { e.printStackTrace() }
        }
    }



    /**
     * Получает текущие результаты Кубка конструкторов Формулы 1 из файла и возвращает строку с информацией о командах.
     * Каждая команда представлена с количеством побед, поул-позиций, лучших кругов и очков.
     *
     * @return Строка с турнирной таблицей Кубка конструкторов или сообщение об ошибке, если данные не могут быть загружены.
     */
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
    /**
     * Отправляет изображение календаря Формулы 1 на 2024 год в чат.
     * Если изображение не может быть загружено, отправляется сообщение об ошибке.
     *
     * @param chatId Идентификатор чата, в который будет отправлено изображение.
     */
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