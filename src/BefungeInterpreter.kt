import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.TextGraphics
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.screen.Screen
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import com.googlecode.lanterna.terminal.swing.AWTTerminalFontConfiguration
import com.googlecode.lanterna.terminal.swing.SwingTerminalFontConfiguration
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.File
import java.io.IOException
import java.lang.NumberFormatException
import java.util.*
import javax.swing.JOptionPane
import javax.swing.Timer
import kotlin.collections.ArrayDeque
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.random.Random

class BefungeInterpreter(grid: Array<Array<Cell>>): ActionListener {
	private val appName: String

	private val width: Int
	private val height: Int
	private val terminalFactory: DefaultTerminalFactory
	private val terminal: Terminal
	private val screen: Screen
	private val textGraphics: TextGraphics
	private val foregroundColor: TextColor
	private val backgroundColor: TextColor
	private val breakpointColor: TextColor
	private var cursorX: Int
	private var cursorY: Int
	private var grid: Array<Array<Cell>>

	private val up = Direction(0, -1)
	private val down = Direction(0, 1)
	private val left = Direction(-1, 0)
	private val right = Direction(1, 0)
	private var direction: Direction

	private val fontPath: String
	private val fontSize: Float
	private val font: Font

	private val extendedMode: Boolean

	private val stack: ArrayDeque<Long>
	private val elementsToDisplay: LinkedList<String>
	private val nElementsToDisplay: Int

	private var stringMode: Boolean
	private var ignoreNextChar: Boolean

	private val timer: Timer
	private val stepTime: Int

	private val random: Random

	private var isRunning: Boolean
	private var automaticExecution: Boolean

	private val missingParameter: String
	private val insertNumber: String
	private val insertCharacter0: String
	private val insertCharacter1: String
	private val insertCharacter2: String
	private val executionCompleted: String
	private val invalidCommand0: String
	private val invalidCommand1: String

	init {
		this.grid = grid.copyOf()

		// Initialise text.
		missingParameter = TEXT_DATA["MISSING_PARAMETER"]
			?: throw IOException("Missing parameter in the text data file: MISSING_PARAMETER")
		insertNumber = TEXT_DATA["INSERT_NUMBER"]
			?: throw IOException("Missing parameter in the text data file: INSERT_NUMBER")
		insertCharacter0 = TEXT_DATA["INSERT_CHARACTER_0"]
			?: throw IOException("Missing parameter in the text data file: INSERT_CHARACTER_0")
		insertCharacter1 = TEXT_DATA["INSERT_CHARACTER_1"]
			?: throw IOException("Missing parameter in the text data file: INSERT_CHARACTER_1")
		insertCharacter2 = TEXT_DATA["INSERT_CHARACTER_2"]
			?: throw IOException("Missing parameter in the text data file: INSERT_CHARACTER_2")
		executionCompleted = TEXT_DATA["EXECUTION_COMPLETED"]
			?: throw IOException("Missing parameter in the text data file: EXECUTION_COMPLETED")
		invalidCommand0 = TEXT_DATA["INVALID_COMMAND_0"]
			?: throw IOException("Missing parameter in the text data file: INVALID_COMMAND_0")
		invalidCommand1 = TEXT_DATA["INVALID_COMMAND_1"]
			?: throw IOException("Missing parameter in the text data file: INVALID_COMMAND_1")

		// Initialise parameters.
		appName = CONFIG_FILE_DATA["INTERPRETER_APP_NAME"]
			?: throw IOException("$missingParameter INTERPRETER_APP_NAME")
		width = grid.size
		height = grid[0].size
		foregroundColor = initialiseColor(
			CONFIG_FILE_DATA["FOREGROUND_COLOR"]
				?: throw IOException("$missingParameter FOREGROUND_COLOR")
		)
		backgroundColor = initialiseColor(
			CONFIG_FILE_DATA["BACKGROUND_COLOR"]
				?: throw IOException("$missingParameter BACKGROUND_COLOR")
		)
		breakpointColor = initialiseColor(
			CONFIG_FILE_DATA["BREAKPOINT_COLOR"]
				?: throw IOException("$missingParameter BREAKPOINT_COLOR")
		)
		fontPath = CONFIG_FILE_DATA["FONT_PATH"]
			?: throw IOException("$missingParameter FONT_PATH")
		try {
			fontSize = CONFIG_FILE_DATA["FONT_SIZE"]?.toFloat()
				?: throw IOException("$missingParameter FONT_SIZE")
		}
		catch (e: NumberFormatException) {
			throw IOException(e)
		}
		extendedMode = CONFIG_FILE_DATA["EXTENDED_MODE"]?.toBooleanStrict()
			?: throw IOException("$missingParameter EXTENDED_MODE")
		try {
			stepTime = CONFIG_FILE_DATA["STEP_TIME"]?.toInt()
				?: throw IOException("$missingParameter STEP_TIME")
		}
		catch (e: NumberFormatException) {
			throw IOException(e)
		}

		// Initialise font.
		font = Font.createFont(Font.TRUETYPE_FONT, File(fontPath)).deriveFont(fontSize)
		val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
		graphicsEnvironment.registerFont(Font.createFont(Font.TRUETYPE_FONT, File(fontPath)))

		// Initialise terminal.
		val terminalSize = TerminalSize(width+14, height+2)
		terminalFactory = DefaultTerminalFactory()
			.setInitialTerminalSize(terminalSize)
			.setTerminalEmulatorTitle(appName)
			.setTerminalEmulatorFontConfiguration(
				SwingTerminalFontConfiguration(
					true, AWTTerminalFontConfiguration.BoldMode.NOTHING, font
				))
		terminal = terminalFactory.createTerminal()
		screen = TerminalScreen(terminal)
		cursorX = 0
		cursorY = 0
		setCursorPosition(cursorX, cursorY)
		screen.startScreen()
		textGraphics = screen.newTextGraphics()

		// Initialise terminal state.
		textGraphics.foregroundColor = foregroundColor
		textGraphics.backgroundColor = backgroundColor
		for (xx in 0 until width) {
			for (yy in 0 until height) {
				if (grid[xx][yy].breakpoint) {
					textGraphics.foregroundColor = breakpointColor
					textGraphics.putString(gridToTerminal(xx, yy), unicodeToString(grid[xx][yy].value.toInt()))
					textGraphics.foregroundColor = foregroundColor
				}
				else {
					textGraphics.putString(gridToTerminal(xx, yy), unicodeToString(grid[xx][yy].value.toInt()))
				}
			}
		}
		// Draw border.
		for (xx in 1..width+13) {
			textGraphics.putString(xx, 0, "═")
			textGraphics.putString(xx, height+1, "═")
		}
		for (yy in 1..height) {
			textGraphics.putString(0, yy, "║")
			textGraphics.putString(width+1, yy, "║")
			textGraphics.putString(width+13, yy, "║")
		}
		textGraphics.putString(0, 0, "╔")
		textGraphics.putString(width+1, 0, "╦")
		textGraphics.putString(width+13, 0, "╗")
		textGraphics.putString(0, height+1, "╚")
		textGraphics.putString(width+1, height+1, "╩")
		textGraphics.putString(width+13, height+1, "╝")
		// Initialise the stack area.
		textGraphics.putString(width+2, 1, "   Stack   ")
		textGraphics.putString(width+1, 2, "╟───────────╢")
		nElementsToDisplay = height - 2
		for (xx in width+2 until width+13) {
			for (yy in 3 until height+1) {
				textGraphics.putString(xx, yy, " ")
			}
		}
		screen.refresh()

		stack = ArrayDeque()
		elementsToDisplay = LinkedList()

		timer = Timer(stepTime, this)
		random = Random

		direction = right

		stringMode = false
		ignoreNextChar = false

		isRunning = true
		automaticExecution = false
	}

	/**
	 * Start the interpreter.
	 */
	fun start() {
		while (isRunning) {
			// Get user input.
			val input = terminal.pollInput()
			if (input != null) {
				when (input.keyType) {
					// Execute one step.
					KeyType.F1 -> executeStep()

					// Toggle automatic execution.
					KeyType.F5 -> {
						automaticExecution = !automaticExecution
						if (automaticExecution) {
							timer.start()
						}
					}

					// The terminal has been closed, stop the loop.
					KeyType.EOF -> {
						isRunning = false
						automaticExecution = false
					}

					else -> {}
				}
			}

			// Sleep to prevent the interpreter to use up too much CPU.
			Thread.sleep(100)
		}
	}

	/**
	 * Execute the action at the current position of the program counter,
	 * refresh the screen and move the program counter.
	 */
	private fun executeStep() {
		// Execute action.
		val character = screen.getBackCharacter(gridToTerminal(cursorX, cursorY)).character
		if (stringMode) {
			if (character != '"') {
				stack.addFirst(character.code.toLong())
			}
			else {
				stringMode = false
			}
		}
		else if (ignoreNextChar) {
			ignoreNextChar = false
		}
		else {
			when (character) {
				// Push numbers.
				'0' -> stack.addFirst(0L)
				'1' -> stack.addFirst(1L)
				'2' -> stack.addFirst(2L)
				'3' -> stack.addFirst(3L)
				'4' -> stack.addFirst(4L)
				'5' -> stack.addFirst(5L)
				'6' -> stack.addFirst(6L)
				'7' -> stack.addFirst(7L)
				'8' -> stack.addFirst(8L)
				'9' -> stack.addFirst(9L)

				'a' -> {
					if (extendedMode) {
						stack.addFirst(10L)
					}
					else {
						invalidCommand(character)
					}
				}

				'b' -> {
					if (extendedMode) {
						stack.addFirst(11L)
					}
					else {
						invalidCommand(character)
					}
				}

				'c' -> {
					if (extendedMode) {
						stack.addFirst(12L)
					}
					else {
						invalidCommand(character)
					}
				}

				'd' -> {
					if (extendedMode) {
						stack.addFirst(13L)
					}
					else {
						invalidCommand(character)
					}
				}

				'e' -> {
					if (extendedMode) {
						stack.addFirst(14L)
					}
					else {
						invalidCommand(character)
					}
				}

				'f' -> {
					if (extendedMode) {
						stack.addFirst(15L)
					}
					else {
						invalidCommand(character)
					}
				}

				// Arithmetic operators.
				'+' -> {
					val n2 = stack.safePop()
					val n1 = stack.safePop()
					stack.addFirst(n1 + n2)
				}

				'-' -> {
					val n2 = stack.safePop()
					val n1 = stack.safePop()
					stack.addFirst(n1 - n2)
				}

				'*' -> {
					val n2 = stack.safePop()
					val n1 = stack.safePop()
					stack.addFirst(n1 * n2)
				}

				'/' -> {
					val n2 = stack.safePop()
					val n1 = stack.safePop()
					stack.addFirst(n1 / n2)
				}

				'%' -> {
					val n2 = stack.safePop()
					val n1 = stack.safePop()
					stack.addFirst(n1 % n2)
				}

				// Comparison operators.
				'!' -> stack.addFirst(if (stack.safePop() == 0L) 1L else 0L)

				'`' -> {
					val n2 = stack.safePop()
					val n1 = stack.safePop()
					stack.addFirst(if (n1 > n2) 1L else 0L)
				}

				// Direction changes.
				'>' -> direction = right
				'<' -> direction = left
				'^' -> direction = up
				'v' -> direction = down

				'?' -> {
					when (random.nextInt(4)) {
						1 -> direction = right
						2 -> direction = left
						3 -> direction = up
						4 -> direction = down
					}
				}

				'_' -> direction = if (stack.safePop() == 0L) left else right
				'|' -> direction = if (stack.safePop() == 0L) up else down

				// Toggle string mode.
				'"' -> stringMode = true

				// Stack manipulation.
				':' -> {
					val nn = stack.safePop()
					stack.addFirst(nn)
					stack.addFirst(nn)
				}

				'$' -> stack.safePop()

				// Output.
				'.' -> print(stack.safePop())
				',' -> print(unicodeToString(stack.safePop().toInt()))

				// Comment.
				'#' -> ignoreNextChar = true

				// Code manipulation.
				'g' -> {
					val n2 = clamp(stack.safePop().toInt(), height)
					val n1 = clamp(stack.safePop().toInt(), height)
					stack.addFirst(screen.getBackCharacter(gridToTerminal(n1, n2)).character.code.toLong())
				}

				'p' -> {
					val n2 = clamp(stack.safePop().toInt(), height)
					val n1 = clamp(stack.safePop().toInt(), height)
					val letter = stack.safePop()
					textGraphics.putString(gridToTerminal(n1, n2), unicodeToString(letter.toInt()))
					grid[n1][n2].value = letter
				}

				// User input.
				'&' -> {
					var number: String
					do {
						number = JOptionPane.showInputDialog(insertNumber)
					} while (!isInteger(number))
					stack.addFirst(number.toLong())
				}

				'~' -> {
					var letter: String?
					do {
						letter = JOptionPane.showInputDialog(insertCharacter0)
					} while ((letter == null) || (letter.isEmpty()))
					if (letter.length != 1) {
						System.err.println("$insertCharacter1 \"$letter\"\n$insertCharacter2")
					}
					stack.addFirst(letter[0].code.toLong())
				}

				// No action.
				' ' -> {}

				// Stop the program.
				'@' -> {
					isRunning = false
					println("\n\n$executionCompleted")
				}

				else -> invalidCommand(character)
			}
		}

		// Display the stack.
		elementsToDisplay.clear()
		// Load elements from the stack.
		for (currentNumber in stack) {
			var element = currentNumber.toString()
			// Format the numbers that are too long to be displayed.
			if (abs(currentNumber) >= 10_000_000_000L) {
				val sign: Int
				if (currentNumber >= 0L) {
					sign = 1
				}
				else {
					sign = -1
					// Remove the minus sign from the string.
					element = element.substring(1)
				}

				// Take only six significant digits, and display using
				// scientific notation.
				// todo: controllare che sia corretto se l'esponente ha piu' di 2 cifre.
				element = element.substring(0, 1) + "." + element.substring(1, 6) +
						"e" + floor(log10((sign * currentNumber).toDouble())).toInt()
				if (sign == -1) {
					element = "-$element"
				}
			}

			// Add padding spaces.
			while (element.length < 11) {
				element = " $element"  // todo: optimise.
			}

			// Check for safety that the string is not too long to be displayed
			// without interfering with the layout.
			if (element.length > 11) {
				element = element.substring(0, 12)
			}

			elementsToDisplay.addLast(element)
			if (elementsToDisplay.size >= nElementsToDisplay) {
				break
			}
		}

		// Add extra elements if necessary.
		while (elementsToDisplay.size < nElementsToDisplay) {
			elementsToDisplay.addFirst("           ")
		}
		// Add a row showing that some elements are not displayed.
		if (stack.size > nElementsToDisplay) {
			elementsToDisplay[nElementsToDisplay - 1] = "    ...    "
		}

		// Draw the stack.
		var rowCounter = 3
		for (element in elementsToDisplay) {
			textGraphics.putString(width+2, rowCounter, element)
			rowCounter++
		}

		// Move the program counter.
		if (isRunning) {
			cursorX = clamp(cursorX + direction.x, width)
			cursorY = clamp(cursorY + direction.y, height)
			setCursorPosition(cursorX, cursorY)
		}

		screen.refresh()
	}

	/**
	 * Execute a step, and check if the program is to be stopped.
	 */
	override fun actionPerformed(e: ActionEvent?) {
		// Execute action.
		if (automaticExecution && isRunning) {
			executeStep()
		}

		// Check if the program has to be stopped.
		if (!(automaticExecution && isRunning)) {
			automaticExecution = false
			timer.stop()
		}

		// Check breakpoint.
		if (grid[cursorX][cursorY].breakpoint) {
			automaticExecution = false
		}
	}

	/**
	 * Set the cursor to the specified coordinates in the grid, after having
	 * compensated for the border.
	 */
	private fun setCursorPosition(cursorX: Int, cursorY: Int) {
		screen.cursorPosition = gridToTerminal(cursorX, cursorY)
	}

	/**
	 * Return the terminal position for the provided coordinates, after having
	 * compensated for the border.
	 */
	private fun gridToTerminal(cursorX: Int, cursorY: Int)
			= TerminalPosition(cursorX+1, cursorY+1)

	/**
	 * Prints an error message for an unrecognised command.
	 */
	private fun invalidCommand(cc: Char) {
		System.err.println("$invalidCommand0 \"$cc\"\n$invalidCommand1")
	}

	private data class Direction(val x: Int, val y: Int)
}

fun ArrayDeque<Long>.safePop(): Long = if (this.size > 0) this.removeFirst() else 0L