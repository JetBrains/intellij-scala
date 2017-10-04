import scala.util.parsing.combinator.JavaTokenParsers

class TestParsers extends JavaTokenParsers {
  /*start*/floatingPointNumber ^^ { _.toDouble }/*end*/
}
//TestParsers.this.Parser[Double]