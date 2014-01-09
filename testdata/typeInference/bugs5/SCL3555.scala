import scala.util.parsing.combinator.JavaTokenParsers
object SCL3555 {
/**
 * Using JavaTokenParsers - whitespace already assume to be separator
 */
object CommandLineParser extends JavaTokenParsers
{
  // any sequence of charaters not including whitespace or a quote
  def noquoteparam: Parser[String] =
    """[^"\s]+""".r

  def command = /*start*/command_spec ~ params_spec ^^ { case left ~ right => FullCommand(left, right) }/*end*/

  def command_spec = (nonQuotedParam | quotedParam) ^^ { param => Command(param.value) }

  // one or more parameters in a row
  def params_spec = param_spec *

  // a literal or a string
  def param_spec = (nonQuotedParam | quotedParam)

  // any param w/out quotes
  def nonQuotedParam = noquoteparam ^^
          {
            s => Param(s)
          }

  // a string literal
  def quotedParam = stringLiteral ^^
          {
            s => Param(s.substring(1, s.length() - 1))
          }

  // the parse command
  def parse(input: String): FullCommand = parseAll(command, input).getOrElse(null)

}

case class Param(value: String)
case class Command(value: String)
case class FullCommand(command: Command, params: List[Param])
}
//CommandLineParser.Parser[SCL3555.FullCommand]