import scala.util.parsing.combinator.JavaTokenParsers

class TestParser extends JavaTokenParsers {

  def result: Parser[Any] = number | string

  def number = new MyParser[Double](floatingPointNumber, _.toDouble)
  def string = new MyParser[String](stringLiteral, s => s)

  class MyParser[T](parser: Parser[String], convert: String => T) extends Parser[T] {

    def valid = "value is" ~ parser ^^ {
      case "value is"~value => convert(/*start*/value/*end*/)
    }

    override def apply(in: Input) = valid(in)

  }

}
//String