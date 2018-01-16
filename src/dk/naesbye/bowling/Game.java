package dk.naesbye.bowling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public final class Game {
	/**
	 * Application spine for import/parsing, scoring, scoreboard, and output for
	 * validation.
	 */
	private List<Frame> frames = new ArrayList<>();
	private String token = null;
	private List<Integer> totalScores = new ArrayList<>();
	private List<Integer> sums = new ArrayList<>();

	public Game() {
		int addedScore = 0;

		String serviceEndpoint = "http://13.74.31.101/api/points";

		int[][] rawFrames = importFromService(serviceEndpoint); // get our score data from the API
		System.out.println("\n*** Scoreboard ***");
		/*
		 * Create and add one frame at the time while counting scores and updating
		 * scoreboard for each frame.
		 */

		for (int i = 0; i < rawFrames.length; i++) {
			addFrame(rawFrames[i][0], rawFrames[i][1]);
			totalScores.add(null); // make sure we have one for each element in rawFrames.

			switch (i) {
			case 0:
				totalScores.set(i, frames.get(i).getScore());
				break;
			case 1:
				addedScore = setScoreOnPreviousFrames(frames.get(i - 1), frames.get(i));
				totalScores.set(i, addedScore + frames.get(i).getScore());
				break;
			default:
				addedScore = setScoreOnPreviousFrames(frames.get(i - 2),frames.get(i - 1), frames.get(i));
				totalScores.set(i, addedScore + frames.get(i).getScore());
				break;
			}

			printScoreboard(i);
		}
		// do the cumulative sums in preparation for sending to API
		for (int i = 0; i < totalScores.size() && i < 10; i++) {
			// if there's an 11th pseudo-frame, its points are already added to the 10th
			sums.add(null);
			if (i == 0) {
				sums.set(0, frames.get(0).getScore());
			} else {
				sums.set(i, sums.get(i - 1) + frames.get(i).getScore());
			}
		}

		sendSumsToAPI(serviceEndpoint);
	}

	void addFrame(int roll1, int roll2) {
		// constructs a new Frame from imported rolls and adds Frame to frames.
		frames.add(new Frame(roll1, roll2));
	}

	void printScoreboard(int frameNumber) {
		// Print out the scoreboard in one line: Roll1 Roll2 (frameScore) = totalScore
		String scoreboardLine = "#" + String.format("%02d", frameNumber + 1) + ": ";

		for (int i = 0; i <= frameNumber; i++) {
			if (frames.get(i).isStrike()) {
				scoreboardLine += "X";
			} else {
				scoreboardLine += frames.get(i).getRoll1();
			}

			if (frames.get(i).isSpare()) {
				scoreboardLine += " /";
			} else if (frames.get(i).isStrike()) {
				scoreboardLine += " ";
			} else {
				scoreboardLine += " " + frames.get(i).getRoll2();
			}

			scoreboardLine += " (" + frames.get(i).getScore() + ") ";

			if (i < frameNumber) {
				scoreboardLine += "; ";
			}
		}
		//scoreboardLine += "= " + totalScores.get(frameNumber);
		System.out.println(scoreboardLine);
	}

	private int setScoreOnPreviousFrames(Frame prevFrame, Frame currFrame) {
		// adds points to the score of the previous Frame if it was a strike/spare.
		if (prevFrame.isStrike()) {
			prevFrame.setScore(prevFrame.getHits() + currFrame.getHits());
			return currFrame.getHits();
		}
		if (prevFrame.isSpare()) {
			prevFrame.setScore(prevFrame.getHits() + currFrame.getRoll1());
			return currFrame.getRoll1();
		}
		return 0;
	}
	private int setScoreOnPreviousFrames(Frame twoPrevFrame,Frame prevFrame, Frame currFrame) {
		// as above, but handles the rare occasion of a triple strike
		if (twoPrevFrame.isStrike() && prevFrame.isStrike()) {
			twoPrevFrame.setScore(twoPrevFrame.getHits() + prevFrame.getHits() + currFrame.getRoll1());
			prevFrame.setScore(prevFrame.getHits() + currFrame.getHits());
			return currFrame.getHits();
		}
		
		if (prevFrame.isStrike()) {
			prevFrame.setScore(prevFrame.getHits() + currFrame.getHits());
			return currFrame.getHits();
		}
		if (prevFrame.isSpare()) {
			prevFrame.setScore(prevFrame.getHits() + currFrame.getRoll1());
			return currFrame.getRoll1();
		}
		return 0;
	}
	int[][] importFromService(String serviceEndpoint) {
		/*
		 * Combined method. Connects to data provider, parses JSON, sets token and
		 * returns game rolls. TODO: Move JSON parsing to separate method.
		 */
		try {
			URL serviceEndpointUrl = new URL(serviceEndpoint);
			HttpURLConnection conn = (HttpURLConnection) serviceEndpointUrl.openConnection();

			conn.setRequestMethod("GET");
			int responsecode = conn.getResponseCode();

			if (responsecode != 200) {
				throw new RuntimeException("Can't get game from service!\nHTTP error: " + responsecode);
			}

			Scanner gameScan = new Scanner(serviceEndpointUrl.openStream());
			String gameData = "";

			while (gameScan.hasNext()) {
				gameData += gameScan.nextLine();
			}

			System.out.println("Got following raw data from endpoint: " + gameData);
			gameScan.close();

			// get token from JSON
			JSONParser parser = new JSONParser();
			JSONObject gameJsonObject = (JSONObject) parser.parse(gameData);
			token = (String) gameJsonObject.get("token"); // we obtain the token from JSON

			// get rolls (labeled as points) from JSON
			JSONArray pointsArray = (JSONArray) gameJsonObject.get("points");
			int[][] resultArray = new int[pointsArray.size()][2];

			for (int i = 0; i < pointsArray.size(); i++) {
				JSONArray pointsObject = (JSONArray) pointsArray.get(i);
				String pointsStringArray = pointsObject.toString();
				String[] items = pointsStringArray.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\s", "")
						.split(",");

				int[] results = new int[items.length];

				for (int j = 0; j < items.length; j++) {
					try {
						results[j] = Integer.parseInt(items[j]);
					} catch (NumberFormatException e) {
						throw new RuntimeException("Bad data in frame");
					}
				}
				resultArray[i] = results;
			}
			return resultArray;
		} catch (IOException | ParseException e) {
			throw new RuntimeException("Invalid URL or data format");
		}
	}

	void sendSumsToAPI(String serviceEndpoint) {
		JSONObject jsonToSend = new JSONObject();
		jsonToSend.put("token", token);
		JSONArray sumsArray = new JSONArray();

		// insert our cumulative scores into the JSON points array
		sumsArray.addAll(sums);

		jsonToSend.put("points", sumsArray);
		System.out.println("******************\n");
		System.out.println("Sending to validator: " + jsonToSend);

		// add our token and sums as parameters to the final URL to send to the API
		String fullPostUrl = serviceEndpoint + "?token=" + token + "&points=" + sumsArray;

		try {
			URL serviceEndpointUrl = new URL(fullPostUrl);
			HttpURLConnection validator = (HttpURLConnection) serviceEndpointUrl.openConnection();
			validator.setDoOutput(true);
			validator.setRequestMethod("POST");

			validator.connect();

			OutputStreamWriter outputStreamWriter = new OutputStreamWriter(validator.getOutputStream());
			outputStreamWriter.write(jsonToSend.toString());
			outputStreamWriter.flush();

			int responseCode = validator.getResponseCode();
			BufferedReader responseReader = null;

			if (responseCode == 200) {
				System.out.println("Token accepted (" + responseCode + ")");
				responseReader = new BufferedReader(new InputStreamReader(validator.getInputStream()));
			} else {
				System.out.println("Token/request rejected (" + responseCode + ")");
				responseReader = new BufferedReader(new InputStreamReader(validator.getErrorStream()));
				System.exit(-1); // if token or connection to validator fails, bail out.
			}

			StringBuilder responseStringBuilder = new StringBuilder();
			String output;
			while ((output = responseReader.readLine()) != null) {
				responseStringBuilder.append(output);
			}

			// Quick hack. Doesn't actually parse result as JSON, just checks for presence of "true".
			boolean validates = responseStringBuilder.toString().contains("true");

			if (validates) {
				System.out.println("Game scores validated successfully.");
			} else {
				System.out.println("Game scores didn't validate.");
				System.out.println(responseStringBuilder);
			}
		} catch (IOException e) {
			throw new RuntimeException("Error in posting data to endpoint!");
		}
	}
}
