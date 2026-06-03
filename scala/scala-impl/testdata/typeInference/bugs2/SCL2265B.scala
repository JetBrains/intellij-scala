import scala.util.matching.Regex
import scala.util.matching.Regex.MatchIterator

def foo() {
  val x: Regex.MatchIterator = ("a".r.findAllIn("blabla"))
  /*start*/x/*end*/
}
//Regex.MatchIterator