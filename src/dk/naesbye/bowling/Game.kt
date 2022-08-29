package dk.naesbye.bowling

import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.json.simple.parser.ParseException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.system.exitProcess

class Game {
    /**
     * Application spine for import/parsing, scoring, scoreboard, and output for
     * validation.
     */
    private val frames: MutableList<Frame> = ArrayList()
    private var token: String? = null
    private val sums: MutableList<Int?> = ArrayList()

    init {
        var addedScore: Int
        val serviceEndpoint = "http://13.74.31.101/api/points"
        val rawFrames = importFromService(serviceEndpoint) // get our score data from the API
        println("\n*** Scoreboard ***")
        /*
		 * Create and add one frame at the time while counting scores and updating
		 * scoreboard for each frame.
		 */
        val totalScores: MutableList<Int?> = ArrayList()
        for (i in rawFrames.indices) {
            addFrame(rawFrames[i][0], rawFrames[i][1])
            totalScores.add(null) // make sure we have one for each element in rawFrames.
            when (i) {
                0 -> totalScores[i] = frames[i].score
                1 -> {
                    addedScore = setScoreOnPreviousFrames(frames[0], frames[i])
                    totalScores[i] = addedScore + frames[i].score
                }

                else -> {
                    addedScore = setScoreOnPreviousFrames(frames[i - 2], frames[i - 1], frames[i])
                    totalScores[i] = addedScore + frames[i].score
                }
            }
            printScoreboard(i)
        }
        // do the cumulative sums in preparation for sending to API
        var i = 0
        while (i < totalScores.size && i < 10) {

            // if there's an 11th pseudo-frame, its points are already added to the 10th
            sums.add(null)
            if (i == 0) {
                sums[0] = frames[0].score
            } else {
                sums[i] = sums[i - 1]?.plus(frames[i].score)
            }
            i++
        }
        sendSumsToAPI(serviceEndpoint)
    }

    private fun addFrame(roll1: Int, roll2: Int) {
        // constructs a new Frame from imported rolls and adds Frame to frames.
        frames.add(Frame(roll1, roll2))
    }

    private fun printScoreboard(frameNumber: Int) {
        // Print out the scoreboard in one line: Roll1 Roll2 (frameScore) = totalScore
        val scoreboardLine = StringBuilder("#" + String.format("%02d", frameNumber + 1) + ": ")
        for (i in 0..frameNumber) {
            if (frames[i].isStrike) {
                scoreboardLine.append("X")
            } else {
                scoreboardLine.append(frames[i].roll1)
            }
            if (frames[i].isSpare) {
                scoreboardLine.append(" /")
            } else if (frames[i].isStrike) {
                scoreboardLine.append(" ")
            } else {
                scoreboardLine.append(" ").append(frames[i].roll2)
            }
            scoreboardLine.append(" (").append(frames[i].score).append(") ")
            if (i < frameNumber) {
                scoreboardLine.append("; ")
            }
        }
        //scoreboardLine += "= " + totalScores.get(frameNumber);
        println(scoreboardLine)
    }

    private fun setScoreOnPreviousFrames(prevFrame: Frame, currFrame: Frame): Int {
        // adds points to the score of the previous Frame if it was a strike/spare.
        if (prevFrame.isStrike) {
            prevFrame.score = prevFrame.hits + currFrame.hits
            return currFrame.hits
        }
        if (prevFrame.isSpare) {
            prevFrame.score = prevFrame.hits + currFrame.roll1
            return currFrame.roll1
        }
        return 0
    }

    private fun setScoreOnPreviousFrames(twoPrevFrame: Frame, prevFrame: Frame, currFrame: Frame): Int {
        // as above, but handles the rare occasion of a triple strike
        if (twoPrevFrame.isStrike && prevFrame.isStrike) {
            twoPrevFrame.score = twoPrevFrame.hits + prevFrame.hits + currFrame.roll1
            prevFrame.score = prevFrame.hits + currFrame.hits
            return currFrame.hits
        }
        if (prevFrame.isStrike) {
            prevFrame.score = prevFrame.hits + currFrame.hits
            return currFrame.hits
        }
        if (prevFrame.isSpare) {
            prevFrame.score = prevFrame.hits + currFrame.roll1
            return currFrame.roll1
        }
        return 0
    }

    private fun importFromService(serviceEndpoint: String?): Array<IntArray> {
        /*
		 * Combined method. Connects to data provider, parses JSON, sets token and
		 * returns game rolls. TODO: Move JSON parsing to separate method.
		 */
        return try {
            val serviceEndpointUrl = URL(serviceEndpoint)
            val conn = serviceEndpointUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            val responsecode = conn.responseCode
            if (responsecode != 200) {
                throw RuntimeException("Can't get game from service!\nHTTP error: $responsecode")
            }
            val gameScan = Scanner(serviceEndpointUrl.openStream())
            val gameData = StringBuilder()
            while (gameScan.hasNext()) {
                gameData.append(gameScan.nextLine())
            }
            println("Got following raw data from endpoint: $gameData")
            gameScan.close()

            // get token from JSON
            val parser = JSONParser()
            val gameJsonObject = parser.parse(gameData.toString()) as JSONObject
            token = gameJsonObject["token"] as String? // we obtain the token from JSON

            // get rolls (labeled as points) from JSON
            val pointsArray = gameJsonObject["points"] as JSONArray
            val resultArray = Array(pointsArray.size) { IntArray(2) }
            for (i in pointsArray.indices) {
                val pointsObject = pointsArray[i] as JSONArray
                val pointsStringArray = pointsObject.toString()
                val items = pointsStringArray.replace("\\[".toRegex(), "").replace("\\]".toRegex(), "")
                    .replace("\\s".toRegex(), "")
                    .split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val results = IntArray(items.size)
                for (j in items.indices) {
                    try {
                        results[j] = items[j].toInt()
                    } catch (e: NumberFormatException) {
                        throw RuntimeException("Bad data in frame")
                    }
                }
                resultArray[i] = results
            }
            resultArray
        } catch (e: IOException) {
            throw RuntimeException("Invalid URL or data format")
        } catch (e: ParseException) {
            throw RuntimeException("Invalid URL or data format")
        }
    }

    private fun sendSumsToAPI(serviceEndpoint: String) {
        val jsonToSend = JSONObject()
        jsonToSend["token"] = token
        val sumsArray = JSONArray()

        // insert our cumulative scores into the JSON points array
        sumsArray.addAll(sums)
        jsonToSend["points"] = sumsArray
        println("******************\n")
        println("Sending to validator: $jsonToSend")

        // add our token and sums as parameters to the final URL to send to the API
        val fullPostUrl = "$serviceEndpoint?token=$token&points=$sumsArray"
        try {
            val serviceEndpointUrl = URL(fullPostUrl)
            val validator = serviceEndpointUrl.openConnection() as HttpURLConnection
            validator.doOutput = true
            validator.requestMethod = "POST"
            validator.connect()
            val outputStreamWriter = OutputStreamWriter(validator.outputStream)
            outputStreamWriter.write(jsonToSend.toString())
            outputStreamWriter.flush()
            val responseCode = validator.responseCode
            val responseReader: BufferedReader
            if (responseCode == 200) {
                println("Token accepted ($responseCode)")
                responseReader = BufferedReader(InputStreamReader(validator.inputStream))
            } else {
                println("Token/request rejected ($responseCode)")
                responseReader = BufferedReader(InputStreamReader(validator.errorStream))
                exitProcess(-1) // if token or connection to validator fails, bail out.
            }
            val responseStringBuilder = StringBuilder()
            var output: String?
            while (responseReader.readLine().also { output = it } != null) {
                responseStringBuilder.append(output)
            }

            // Quick hack. Doesn't actually parse result as JSON, just checks for presence of "true".
            val validates = responseStringBuilder.toString().contains("true")
            if (validates) {
                println("Game scores validated successfully.")
            } else {
                println("Game scores didn't validate.")
                println(responseStringBuilder)
            }
        } catch (e: IOException) {
            throw RuntimeException("Error in posting data to endpoint!")
        }
    }
}