# Bowling Scoreboard Simulator 2018

 A 10-pin bowling scoring program, made for a job interview. Probably not overly useful for other people.
 
 It is written for Java 1.8 and uses the [json-simple](https://code.google.com/archive/p/json-simple/) library for JSON parsing. This is included, but you may need to add the library /lib/json-simple-1.1.1.jar to your build path.

 The assumption is that scores come from an external source, and scores get printed out for each frame (2 rolls).
 The number of rolls is not fixed, but this program limits it to 11 frames (10 + 1-2 rolls if the 10th is a strike or spare).
 
 License is [CC0](https://creativecommons.org/publicdomain/zero/1.0/) aka public domain.

Output is to the console/standard output. A scoreboard line lists the score for each frame played, and frame scores will be retroactively changed in the case of strikes and spares.

The format is as follows:
\#Frame number: roll1 roll2 (totalHits) ; <next frame>

In the end the result is sent to the API for verification of token and points. The result is interpreted and displayed.

 
