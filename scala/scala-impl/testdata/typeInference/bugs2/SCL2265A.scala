import scala.util.matching.Regex
import scala.util.matching.Regex.MatchIterator

def foo() {
  val x: Regex.MatchIterator = ("a".r.findAllIn("blabla"))
  bar(x)
}

def bar(features: MatchIterator) {
  /*start*/features/*end*/
}
//Regex.MatchIterator