import scala.util.matching.Regex
import scala.util.matching.Regex.Match

trait LineParser[M] {
  def pattern: Regex
  def unapply(line: String): Option[M] = {
    pattern.findFirstMatchIn(line) map { matches =>
      // Invoking this method rather than directly returning the tuple for the class below triggers the spurious red highlighting
      extractMatches(matches)
    }
  }
  def extractMatches(matchData: Match): M
}

object TableLineParser extends LineParser[(String, Int, Int)] {
  val pattern = """... etc ...""".r
  override def extractMatches(matchData: Match) = {
    (matchData.group(1), matchData.group(2).toInt, matchData.group(3).toInt)
  }
}

"text" match {
  // The error is here. `tableName` is (String, Int, Int) and `capacity`/`button` are
  // undefined rather than `tableName` being String and the other
  // two each Int.
  case TableLineParser(tableName , capacity, button) =>
    /*start*/tableName/*end*/
  // ...
}
//String