package org.jetbrains.plugins.hocon.manipulators

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.AbstractElementManipulator
import org.jetbrains.plugins.hocon.lexer.HoconLexer
import org.jetbrains.plugins.hocon.psi._

/**
  * Manipulator for unquoted string literals. For now, it is registered for [[org.jetbrains.plugins.hocon.psi.HoconPsiElement]].
  * It will be registered for dedicated class after proper hierarchy of PSI classes for HOCON is implemented.
  */
class HStringManipulator extends AbstractElementManipulator[HString] {

  import org.jetbrains.plugins.hocon.lexer.HoconTokenType._
  import org.jetbrains.plugins.hocon.parser.HoconElementType._

  def handleContentChange(str: HString, range: TextRange, newContent: String): HString = {
    val strType = str.stringType
    val oldText = str.getText

    val escapedContent = strType match {
      case MultilineString => newContent
      case _ => StringUtil.escapeStringCharacters(newContent)
    }

    val needsQuoting = strType == UnquotedString &&
      (newContent.isEmpty || newContent.startsWith(" ") || newContent.endsWith(" ")
        || (str.elementType == KeyPart && newContent.contains('.'))
        || newContent.exists(HoconLexer.ForbiddenChars.contains) || escapedContent != newContent)

    val unquotedText = oldText.substring(0, range.getStartOffset) + escapedContent + oldText.substring(range.getEndOffset)
    val quotedText = if (needsQuoting) "\"" + unquotedText + "\"" else unquotedText

    val newString = str.elementType match {
      case StringValue => HoconPsiElementFactory.createStringValue(quotedText, str.getManager)
      case KeyPart => HoconPsiElementFactory.createKeyPart(quotedText, str.getManager)
      case IncludeTarget => HoconPsiElementFactory.createIncludeTarget(quotedText, str.getManager)
    }
    str.getFirstChild.replace(newString.getFirstChild)

    str
  }

  override def getRangeInElement(element: HString): TextRange = element.stringType match {
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
