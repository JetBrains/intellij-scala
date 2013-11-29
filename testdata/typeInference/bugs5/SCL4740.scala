import scala.reflect.io.File

object PhoneCode {

  def main(args: Array[String]) {
    //load all 3 files - and according to the spec, not into the memory. just open the file and have an iterator sucking lines from it - (1 line only :))
    val Array(dictEntries, phoneEntries, testResult) = args.map(fileName => File("resource/" + fileName).lines())
    //function to create strings without noise chars, 1 line
    val cleanString = (s: String) => s.filterNot(Set('-', '"', '/'))
    //prepare lookup table number -> all words, 8 lines
    val dictEntriesDigified2Words = {
      val wordToDigits = {
        val mappingReversed = (for (chars2Digit <- Array("e", "jnq", "rwx", "dsy", "ft", "am", "civ", "bku", "lop", "ghz").zipWithIndex;
                                    char <- (chars2Digit._1 ++ chars2Digit._1.toUpperCase)) yield (char -> chars2Digit._2)).toMap
        (word: String) => word.map(mappingReversed).mkString
      }
      dictEntries.toArray.groupBy(cleanString andThen wordToDigits)
    }
    //i love to create classes nested inside methods just before i need them :) (6 lines)
    case class Step(translated: String, remaining: String, original: String) {
      def canFallBackToDigit = !translated.lastOption.exists(_.isDigit)
      def asFallback(notMatched: String) = if (canFallBackToDigit) List(copy(translated + " " + notMatched.head, notMatched.tail, original)) else Nil
      def format = original + ": " + translated.trim
      def ifFinished = if (remaining.isEmpty) Some(List(this)) else None
    }
    val result = phoneEntries.flatMap(phoneNumber => {
      // 16 lines
      def collectPossibleTranslations(current: Step): Seq[Step] = {
        current.ifFinished.getOrElse({
          def allMatches(matchAgainst: String) = {
            //collect all possible next translation steps of the remaining numbers
            val matchingWords = (for (len <- 1 to matchAgainst.length;
                                      opt <- dictEntriesDigified2Words.get(matchAgainst.take(len))) yield opt).flatten
            if (matchingWords.nonEmpty) //spead the tree
              for ((translated, remaining) <- matchingWords.map(e => e -> matchAgainst.drop(e.count(_.isLetter)))) yield
                (current.copy(current.translated + " " + translated, remaining))
            else current.asFallback(matchAgainst)
          }
          allMatches(current.remaining).flatMap(collectPossibleTranslations)
        })
      }
      /*start*/collectPossibleTranslations(Step("", cleanString(phoneNumber), phoneNumber))/*end*/
    }).toSet
    //actual solution ends here, total non comment line code: 32 (+6 for package, import, main method)
    //result checking to prove it's working
    val resultFormatted = result.map(_.format)
    val correctResult = testResult.toSet
    val foundButNotExpected = resultFormatted.filterNot(correctResult).toList.sorted
    val expectedButNotFound = correctResult.filterNot(resultFormatted).toList.sorted
    println("should not be there: " + foundButNotExpected)
    println("missing:" + expectedButNotFound)
    println("both results really equal: " + (resultFormatted == correctResult))
  }
}
//Seq[Step]