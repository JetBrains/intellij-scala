import scala.util.parsing.combinator.JavaTokenParsers

case class Coords(x: Double, y: Double)

class TestParsers extends JavaTokenParsers {
  lazy val number: Parser[Double] = floatingPointNumber ^^ { _.toDouble }

  lazy val coords: Parser[Coords] = {
    "[" ~> number ~ "," ~ number <~ "]" ^^ {
      case ~(~(x, ","), y) =>
        /*start*/(x, y)/*end*/
        Coords(x, y)   // <== wrong type mismatch for x and y
    }
  }
}
//(Double, Double)