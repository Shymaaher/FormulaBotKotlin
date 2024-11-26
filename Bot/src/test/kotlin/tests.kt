import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll

class SimpleBotTest {

    companion object {
        private lateinit var bot: SimpleBot // Объявляем bot как lateinit переменную

        @BeforeAll
        @JvmStatic
        fun setup() {
            bot = SimpleBot() // Инициализируем bot перед всеми тестами
        }

        @AfterAll
        @JvmStatic
        fun printTestResults() {
            println("Все тесты выполнены успешно!")
        }
    }

    @Test
    fun testGetBotUsername() {
        assertEquals("Formula1CalendarBot", bot.getBotUsername())
    }

    @Test
    fun testGetBotToken() {
        assertNotNull(bot.getBotToken())
    }

    @Test
    fun testQuizQuestionCreation() {
        val question1 = bot.quizQuestions[0]
        assertEquals("Кто выиграл чемпионат мира Формулы 1 в 2020 году?", question1.question)
        assertEquals(4, question1.answers.size)
        assertEquals(0, question1.correctAnswerIndex)
    }

    @Test
    fun testShowMainButtons_NotEmpty() {
        val buttons = bot.showMainButtons()
        assertTrue(buttons.keyboard.isNotEmpty())
    }

    @Test
    fun testShowCarsButtons_ContainsExpectedButtons() {
        val buttons = bot.showCarsButtons()
        val buttonTexts = buttons.keyboard.flatMap { it.toList() }.map { it.text }
        assertTrue(buttonTexts.contains("Oracle Red Bull Racing"))
        assertTrue(buttonTexts.contains("Назад в главное меню"))
    }
}
