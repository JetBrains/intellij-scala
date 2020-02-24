package scala.quoted.show

trait SyntaxHighlight {
  def highlightKeyword(str: String): String
  def highlightTypeDef(str: String): String
  def highlightLiteral(str: String): String
  def highlightValDef(str: String): String
  def highlightOperator(str: String): String
  def highlightAnnotation(str: String): String
  def highlightString(str: String): String
  def highlightTripleQs: String
}

object SyntaxHighlight {
  def ANSI: SyntaxHighlight = ???
  def plain: SyntaxHighlight = ???
}
