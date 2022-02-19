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
import java.awt.*
import java.io.File
import java.io.IOException
import java.lang.NumberFormatException
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

class BefungeEditor(inputFilePath: String = "") {
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
	private val grid: Array<Array<Cell>>

	private val fontPath: String
	private val fontSize: Float
	private val font: Font

	private var isRunning: Boolean

	private val missingParameter: String
	private val wronglyFormattedFile: String
	private val wrongFileExtension0: String
	private val wrongFileExtension1: String
	private val askForSave0: String
	private val askForSave1: String
	private val programSavedTo: String

	init {
		// Initialise text.
		missingParameter = TEXT_DATA["MISSING_PARAMETER"]
			?: throw IOException("Missing parameter in the text data file: MISSING_PARAMETER")
		wronglyFormattedFile = TEXT_DATA["WRONGLY_FORMATTED_FILE"]
			?: throw IOException("Missing parameter in the text data file: WRONGLY_FORMATTED_FILE")
		wrongFileExtension0 = TEXT_DATA["WRONG_FILE_EXTENSION_0"]
			?: throw IOException("Missing parameter in the text data file: WRONG_FILE_EXTENSION_0")
		wrongFileExtension1 = TEXT_DATA["WRONG_FILE_EXTENSION_1"]
			?: throw IOException("Missing parameter in the text data file: WRONG_FILE_EXTENSION_1")
		askForSave0 = TEXT_DATA["ASK_FOR_SAVE_0"]
			?: throw IOException("Missing parameter in the text data file: ASK_FOR_SAVE_0")
		askForSave1 = TEXT_DATA["ASK_FOR_SAVE_1"]
			?: throw IOException("Missing parameter in the text data file: ASK_FOR_SAVE_1")
		programSavedTo = TEXT_DATA["PROGRAM_SAVED_TO"]
			?: throw IOException("Missing parameter in the text data file: PROGRAM_SAVED_TO")

		// Initialise parameters.
		appName = CONFIG_FILE_DATA["EDITOR_APP_NAME"]
			?: throw IOException("$missingParameter EDITOR_APP_NAME")
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

		if (inputFilePath == "") {
			try {
				width = CONFIG_FILE_DATA["WIDTH"]?.toInt()
					?: throw IOException("$missingParameter WIDTH")
			}
			catch (e: NumberFormatException) {
				throw IOException(e)
			}
			try {
				height = CONFIG_FILE_DATA["HEIGHT"]?.toInt()
					?: throw IOException("$missingParameter HEIGHT")
			}
			catch (e: NumberFormatException) {
				throw IOException(e)
			}

			// Initialise the grid with the value of the space character.
			grid = Array(width) {
				Array(height) {
					Cell(0x20L)
				}
			}
		}
		else {
			// Check file extension.
			if (
				(inputFilePath.contains("."))
				&& (inputFilePath.substring(inputFilePath.lastIndexOf(".")) == ".bf")
			) {
				// Read input data.
				val fileData = File(inputFilePath)
					.readText()
					.lines()
				// Check that every line has the same length.
				if (fileData.any { it.length != fileData[0].length }) {
					throw IOException("$wronglyFormattedFile $inputFilePath")
				}

				width = fileData[0].length
				height = fileData.size

				// Initialise the grid with the file data.
				grid = Array(width) { xx ->
					Array(height) { yy ->
						Cell(fileData[yy][xx].code.toLong())
					}
				}
			}
			else {
				// todo: close terminal.
				throw IOException("$wrongFileExtension0 $inputFilePath\n$wrongFileExtension1")
			}
		}

		// Initialise font.
		font = Font.createFont(Font.TRUETYPE_FONT, File(fontPath)).deriveFont(fontSize)
		val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
		graphicsEnvironment.registerFont(Font.createFont(Font.TRUETYPE_FONT, File(fontPath)))

		// Initialise terminal.
		val terminalSize = TerminalSize(width+2, height+2)
		terminalFactory = DefaultTerminalFactory()
			.setInitialTerminalSize(terminalSize)
			.setTerminalEmulatorTitle(appName)
			.setTerminalEmulatorFontConfiguration(SwingTerminalFontConfiguration(
				true, AWTTerminalFontConfiguration.BoldMode.NOTHING, font
			))
		terminal = terminalFactory.createTerminal()
		screen = TerminalScreen(terminal)
		cursorX = 0
		cursorY = 0
		setCursorPosition(cursorX, cursorY)
		screen.startScreen()
		textGraphics = screen.newTextGraphics()
		textGraphics.foregroundColor = foregroundColor
		textGraphics.backgroundColor = backgroundColor

		// Initialise the terminal with the values from the grid.
		for (xx in 0 until width) {
			for (yy in 0 until height) {
				textGraphics.putString(gridToTerminal(xx, yy), unicodeToString(grid[xx][yy].value.toInt()))
			}
		}

		// Draw border.
		for (xx in 1..width) {
			textGraphics.putString(xx, 0, "═")
			textGraphics.putString(xx, height+1, "═")
		}
		for (yy in 1..height) {
			textGraphics.putString(0, yy, "║")
			textGraphics.putString(width+1, yy, "║")
		}
		textGraphics.putString(0, 0, "╔")
		textGraphics.putString(width+1, 0, "╗")
		textGraphics.putString(0, height+1, "╚")
		textGraphics.putString(width+1, height+1, "╝")
		screen.refresh()

		isRunning = true
	}

	/**
	 * Start the editor.
	 */
	fun start() {
		while (isRunning) {
			// Get user input.
			val input = terminal.readInput()
			println(input)

			when (input.keyType) {
				// Move the cursor.
				KeyType.ArrowUp -> {
					cursorY = clamp(--cursorY, height)
					setCursorPosition(cursorX, cursorY)
				}

				KeyType.ArrowDown -> {
					cursorY = clamp(++cursorY, height)
					setCursorPosition(cursorX, cursorY)
				}

				KeyType.ArrowLeft -> {
					cursorX = clamp(--cursorX, width)
					setCursorPosition(cursorX, cursorY)
				}

				KeyType.ArrowRight -> {
					cursorX = clamp(++cursorX, width)
					setCursorPosition(cursorX, cursorY)
				}

				KeyType.Home -> {
					cursorX = 0
					setCursorPosition(cursorX, cursorY)
				}

				KeyType.End -> {
					cursorX = width - 1
					setCursorPosition(cursorX, cursorY)
				}

				KeyType.PageUp -> {
					cursorY = 0
					setCursorPosition(cursorX, cursorY)
				}

				KeyType.PageDown -> {
					cursorY = height - 1
					setCursorPosition(cursorX, cursorY)
				}

				// Write character on the terminal.
				KeyType.Character -> {
					if (grid[cursorX][cursorY].breakpoint) {
						textGraphics.foregroundColor = breakpointColor
						textGraphics.putString(gridToTerminal(cursorX, cursorY), input.character.toString())
						textGraphics.foregroundColor = foregroundColor
					}
					else {
						textGraphics.putString(gridToTerminal(cursorX, cursorY), input.character.toString())
					}

					grid[cursorX][cursorY].value = input.character.code.toLong()
				}

				// Start interpreter.
				KeyType.F1 -> {
					BefungeInterpreter(grid).start()
				}

				// Toggle breakpoint.
				KeyType.F8 -> {
					grid[cursorX][cursorY].breakpoint = !grid[cursorX][cursorY].breakpoint

					if (grid[cursorX][cursorY].breakpoint) {
						textGraphics.foregroundColor = breakpointColor
						textGraphics.putString(gridToTerminal(cursorX, cursorY),
											   screen.getBackCharacter(gridToTerminal(cursorX, cursorY)).character.toString())
						textGraphics.foregroundColor = foregroundColor
					}
					else {
						textGraphics.putString(gridToTerminal(cursorX, cursorY),
											   screen.getBackCharacter(gridToTerminal(cursorX, cursorY)).character.toString())
					}
				}

				// Load from file.
				KeyType.F11 -> {
					// Ask to save first.
					if (JOptionPane.showConfirmDialog(
							null,
							askForSave0,
							askForSave1,
							JOptionPane.YES_NO_OPTION
						) == JOptionPane.YES_OPTION) {
						outputToFile()
					}

					// Select the file to open.
					val fileChooser = JFileChooser()
					fileChooser.fileFilter = FileNameExtensionFilter("Befunge files", "bf")
					val filePath: String = if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
						fileChooser.selectedFile.absolutePath
					}
					else {
						""
					}
					// Check extension.
					if (
						(filePath.contains("."))
						&& (filePath.substring(filePath.lastIndexOf(".")) == ".bf")
					) {
						// Open file.
						// todo: fare in modo che BefungeEditor implementi Runnable, o resta bloccato qui e la finestra vecchia non si chiude.
						BefungeEditor(filePath).start()
						isRunning = false
					}
					else if (filePath != "") {
						System.err.println("$wrongFileExtension0 $filePath\n$wrongFileExtension1")
					}
				}

				// Output to file.
				KeyType.F12 -> outputToFile()

				// The terminal has been closed, stop the loop.
				KeyType.EOF -> isRunning = false

				else -> {}
			}

			screen.refresh()
		}

		// Close the terminal.
		screen.close()
		terminal.close()
	}

	/**
	 * Ask the user for a file path, and if the extension of the file is .bf
	 * save the content of the grid into that file.
	 */
	private fun outputToFile() {
		// Select file path.
		val fileChooser = JFileChooser()
		fileChooser.fileFilter = FileNameExtensionFilter("Befunge files", "bf")
		val filePath: String = if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
			fileChooser.selectedFile.absolutePath
		}
		else {
			""
		}
		// Check extension.
		if (
			(filePath.contains("."))
			&& (filePath.substring(filePath.lastIndexOf(".")) == ".bf")
		) {
			// Write file.
			val output = buildString {
				for (yy in 0 until height) {
					for (xx in 0 until width) {
						this.append(unicodeToString(grid[xx][yy].value.toInt()))
					}
					if (yy != height - 1) {
						this.append("\n")
					}
				}
			}
			File(filePath).writeText(output)

			println("$programSavedTo $filePath")
		}
		else if (filePath != "") {
			System.err.println("$wrongFileExtension0 $filePath\n$wrongFileExtension1")
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
}