package intellijhocon.formatting

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.formatting.{Indent, Spacing}
import com.intellij.lang.ASTNode
import intellijhocon.parser.{HoconElementSets, HoconElementType}
import intellijhocon.Util
import com.intellij.psi.TokenType
import intellijhocon.lexer.{HoconTokenSets, HoconTokenType}

object FormattingManager {

  import HoconElementSets._
  import HoconElementType._
  import HoconTokenType._
  import HoconTokenSets._
  import Util._

  val ForcedLeaf = Path | UnquotedString | Number | Null | Boolean | TokenType.ERROR_ELEMENT

  def getSpacing(settings: CodeStyleSettings, leftBlock: HoconBlock, rightBlock: HoconBlock) =
    Spacing.createSafeSpacing(true, 2)

  def getIndent(settings: CodeStyleSettings, parent: ASTNode, child: ASTNode) =
    (parent.getElementType, child.getElementType) match {
      case (Object, Include | ObjectField | Comma | Comment.extractor()) |
           (Array, Value | Comma | Comment.extractor()) =>
        Indent.getNormalIndent
      case (Include, Included) |
           (ObjectField, PathValueSeparator.extractor() | Value) =>
        Indent.getContinuationIndent
      case _ =>
        Indent.getNoneIndent

    }

  def getChildIndent(settings: CodeStyleSettings, parent: ASTNode) = parent.getElementType match {
    case Object | Array => Indent.getNormalIndent
    case Include | ObjectField => Indent.getContinuationIndent
    case _ => Indent.getNoneIndent
  }

  def getChildren(node: ASTNode): Iterator[ASTNode] = node.getElementType match {
    case ForcedLeaf.extractor() =>
      Iterator.empty
    case HoconFileElementType | Object =>
      node.childrenIterator.flatMap(child => child.getElementType match {
        case ObjectEntries => getChildren(child)
        case _ => Iterator(child)
      })
    case _ => node.childrenIterator
  }
}
