import scala.util.parsing.combinator.RegexParsers

object SCL6157 {

  object Grammar extends RegexParsers {
    val Blah: Parser[(Int, String)] = "\\d+".r ~ ".+".r ^^ {
      case a ~ b => (a.toInt, b)
    }
  }

  import Grammar._

  def test1(pr: ParseResult[(Int, String)]): Int = pr match {
    case Success((i,_), _) => /*start*/i/*end*/
  }
}
//Int