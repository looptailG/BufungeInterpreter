import com.googlecode.lanterna.TextColor
import java.io.File
import java.io.IOException

const val CONFIG_FILE_PATH = "config/config.csv"
val CONFIG_FILE_DATA = loadParameters(CONFIG_FILE_PATH)
val TEXT_DATA = loadParameters(
	"${CONFIG_FILE_DATA["TEXT_FILE_PATH"]}${CONFIG_FILE_DATA["LANGUAGE"]}.csv"
)

val wronglyFormattedNumber = TEXT_DATA["WRONGLY_FORMATTED_NUMBER"]
	?: throw IOException("Missing parameter in the text data file: WRONGLY_FORMATTED_NUMBER")
val wronglyFormattedColor0 = TEXT_DATA["WRONGLY_FORMATTED_COLOR_0"]
	?: throw IOException("Missing parameter in the text data file: WRONGLY_FORMATTED_COLOR_0")
val wronglyFormattedColor1 = TEXT_DATA["WRONGLY_FORMATTED_COLOR_1"]
	?: throw IOException("Missing parameter in the text data file: WRONGLY_FORMATTED_COLOR_1")
val wronglyFormattedColor2 = TEXT_DATA["WRONGLY_FORMATTED_COLOR_2"]
	?: throw IOException("Missing parameter in the text data file: WRONGLY_FORMATTED_COLOR_2")

fun main(args: Array<String>) {
	// todo: check if the args contains a .bf file, and use it to initialise the editor.

	// Start the editor.
	BefungeEditor().start()
}

/**
 * Load the content of the input .csv file into the returned Map, using the
 * first column as Key and the second as Value.
 */
private fun loadParameters(filePath: String): Map<String, String> {
	val fileData = mutableMapOf<String, String>()

	// Read the file.
	val fileLines = File(filePath)
		.readText()
		.lines()
	for (line in fileLines) {
		// Skip comment or empty lines.
		if (line.startsWith("#") || (line == "")) continue

		// Insert data in the map.
		val lineData = line
			.split(",", limit = 3)
			.map { it.trim() }
		if (lineData.size < 2) {
			throw IOException("Configuration file incorrectly formatted at line: \n$line")
		}
		fileData[lineData[0]] = lineData[1]
	}

	return fileData
}

/**
 * Clamp the value of aa to be between 0 (inclusive) and bb (exclusive).
 */
fun clamp(aa: Int, bb: Int): Int {
	var clampedValue = aa
	while (clampedValue < 0) clampedValue += bb
	return clampedValue % bb
}

/**
 * Return true if the input String can be parsed as an integer number, by
 * matching it with the "-?\\d+" regex.  Otherwise, it displays an error message
 * and return false.
 */
fun isInteger(numericString: String): Boolean {
	val integerRegex = Regex("-?\\d+")
	return if (integerRegex.matches(numericString)) {
		true
	}
	else {
		System.err.println("$wronglyFormattedNumber \"$numericString\"")
		return false
	}
}

/**
 * Convert a Unicode code point (in decimal) to the corresponding String.
 */
fun unicodeToString(codePoint: Int) = String(intArrayOf(codePoint), 0, 1)

/**
 * Initialise a TextColor from a String formatted as RED_GREEN_BLUE.
 */
fun initialiseColor(colors: String): TextColor {
	val rgb: List<Int>
	try {
		rgb = colors.split("_").map { it.toInt() }
		if (rgb.size != 3) {
			throw IOException("$wronglyFormattedColor0\n$colors\n$wronglyFormattedColor1")
		}
		if (rgb.any { (it > 255) || (it < 0) }) {
			throw IOException("$wronglyFormattedColor0\n$colors\n$wronglyFormattedColor2")
		}
		return TextColor.RGB(rgb[0], rgb[1], rgb[2])
	}
	catch (e: NumberFormatException) {
		throw IOException(e)
	}
}

data class Cell(var value: Long, var breakpoint: Boolean = false)