package intellijhocon
package manipulators

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.AbstractElementManipulator
import lexer.{HoconLexer, HoconTokenType}
import parser.HoconElementType
import psi.{HString, HoconPsiElement, HoconPsiElementFactory}

/**
 * Manipulator for unquoted string literals. For now, it is registered for [[HoconPsiElement]].
 * It will be registered for dedicated class after proper hierarchy of PSI classes for HOCON is implemented.
 */
class HStringManipulator extends AbstractElementManipulator[HString] {

  import HoconElementType._
  import HoconTokenType._

  def handleContentChange(str: HString, range: TextRange, newContent: String) = {
    val strType = str.stringType
    val oldText = str.getText

    val escapedContent = strType match {
      case MultilineString => newContent
      case _ => StringUtil.escapeStringCharacters(newContent)
    }

    val needsQuoting = strType == UnquotedString &&
            (newContent.isEmpty || newContent.startsWith(" ") || newContent.endsWith(" ")
                    || newContent.exists(HoconLexer.ForbiddenChars.contains) || escapedContent != newContent)

    val unquotedText = oldText.substring(0, range.getStartOffset) + escapedContent + oldText.substring(range.getEndOffset)
    val quotedText = if (needsQuoting) "\"" + unquotedText + "\"" else unquotedText

    val newString = HoconPsiElementFactory.createString(quotedText, str.getManager)
    str.getFirstChild.replace(newString.getFirstChild)

    str
  }

  override def getRangeInElement(element: HString) = element.stringType match {
    case UnquotedString =>
      new TextRange(0, element.getTextLength)
    case QuotedString =>
      new TextRange(1, element.getTextLength - (if (element.isClosed) 1 else 0))
    case MultilineString =>
      new TextRange(3, element.getTextLength - (if (element.isClosed) 3 else 0))
    case _ =>
      super.getRangeInElement(element)
  }
}
