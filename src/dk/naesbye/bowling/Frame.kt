package dk.naesbye.bowling

class Frame internal constructor(roll1: Int, roll2: Int) {
    val roll1: Int
    val roll2: Int
    val hits: Int
    var score: Int
    /** A bunch of basic getters and setters to allow for Game to calculate and set scores.  */
    var isSpare = false
    var isStrike = false

    init {
        framesLength++
        // set base values
        this.roll1 = roll1
        this.roll2 = roll2
        hits = roll1 + roll2
        score = hits
        validate()

        // detect strike or spare. They are mutually exclusive with strike taking priority.
        if (roll1 == 10) {
            isStrike = true
        } else if (hits == 10) {
            isSpare = true
        }
        if (framesLength == 10 && isStrike) {
            rollsInEleven = 2
        }
        if (framesLength == 10 && isSpare) {
            rollsInEleven = 1
        }
    }

    private fun validate() {
        // check Frame for compliance with game rules.

        // Limit game to a maximum of 11 frames. Only allow frame
        if (framesLength > 11 || framesLength == 11 && rollsInEleven == 0) {
            throw RuntimeException("Too many frames in game")
        }

        // Import substitutes null values for zero. As a result, we can't see if player
        // rolls 0 hits where a roll was not allowed. This is acceptable as it doesn't
        // affect score.
        // The user is allowed one roll after a spare in frame #10. If the user scores
        // in a second roll after a spare in frame # 10, this is a violation.
        if (framesLength == 11 && rollsInEleven == 1 && roll2 > 0) {
            throw RuntimeException("Too many rolls after spare in frame 10")
        }

        // If the 10th frame is a strike, the player gets two extra rolls. If the first
        // of these is a strike, the total of the rolls can exceed 10.
        // The two extra rolls are received from the API as frame #11.
        // In frame #11 the maximum hits is 20.
        if (hits > 10 && framesLength <= 10 || hits > 20 && framesLength == 11) {
            throw RuntimeException("Too many pins")
        }

        // roll result is out of bounds
        if (roll1 < 0 || roll2 < 0 || roll1 > 10 || roll2 > 10) {
            throw RuntimeException("Invalid number of hits")
        }
    }

    companion object {
        private var framesLength = 0
        private var rollsInEleven = 0
    }
}