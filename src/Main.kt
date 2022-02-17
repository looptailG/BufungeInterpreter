import java.io.File
import java.io.IOException

const val CONFIG_FILE_PATH = "config/config.csv"
val CONFIG_FILE_DATA = loadParameters(CONFIG_FILE_PATH)
val TEXT_DATA = loadParameters(
	"${CONFIG_FILE_DATA["TEXT_FILE_PATH"]}${CONFIG_FILE_DATA["LANGUAGE"]}.csv"
)

fun main() {
	println("hello world!")
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
		// Skip comment lines.
		if (line.startsWith("#")) continue

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
