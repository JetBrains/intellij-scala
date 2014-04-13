package intellijhocon

import com.intellij.lang.{ASTNode, PsiBuilder, PsiParser}
import com.intellij.psi.tree.IElementType

class HoconPsiParser extends PsiParser {

  import GenParser._
  import HoconTokenType._
  import HoconElementType._

  def hoconFile = hoconObject | objectEntries

  def hoconObject: GenParser = LBrace ~ objectEntries ~ RBrace as Object

  def objectEntries = (objectEntry ~ separator).* ~ objectEntry.? as ObjectEntries

  def objectEntry = include | objectField

  def include = "include" ~ included as Include

  def included = QuotedString | ("url(" | "file(" | "classpath(") ~ QuotedString ~ ")" as Included

  def objectField = path ~ (hoconObject | (Colon | Equals | PlusEquals) ~ value) as ObjectField

  def path = pathElement ~ (Dot ~ pathElement).* as Path

  def pathElement = (unquotedPathString | QuotedString | MultilineString).+ as PathElement

  def unquotedPathString = (UnquotedChars | Integer | Decimal).+ as UnquotedString

  def array = LBracket ~ (value ~ separator).* ~ value.? ~ RBracket as Array

  def value: GenParser = (nullValue | boolean | number | string | reference | hoconObject | array).+ as Value

  def string = unquotedString | QuotedString | MultilineString

  def unquotedString = (UnquotedChars | Integer | Decimal | Dot).+ as UnquotedString

  def reference = RefStart ~ path ~ RefEnd as Reference

  def number = Integer ~ (Dot ~ Decimal).? as Number

  def nullValue = "null" as Null

  def boolean = "true" | "false" as Boolean

  def separator = NewLine | Comma

  def recoverToSeparator(message: String) =
    not(Comma, NewLine, RBrace, RBracket).* asError message

  def recoverToValue(message: String) =
    not(Colon, Equals, PlusEquals, LBrace, Comma, NewLine, RBrace, RBracket).* asError message

  def traverse(node: ASTNode, indent: Int = 0) {
    println("  " * indent + node)
    node.getChildren(null).foreach(traverse(_, indent + 1))
  }

  def parse(root: IElementType, builder: PsiBuilder): ASTNode = {
    val file = builder.mark()
    hoconFile.parse(builder)
    while (builder.getTokenType != null) {
      builder.advanceLexer()
    }
    file.done(root)
    builder.getTreeBuilt
  }

}
