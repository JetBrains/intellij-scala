package org.jetbrains.plugins.hocon.manipulators

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{AbstractElementManipulator, PsiManager}
import org.jetbrains.plugins.hocon.lexer.HoconLexer
import org.jetbrains.plugins.hocon.psi.{HKey, HoconPsiElementFactory}

/**
  * @author ghik
  */
class HKeyManipulator extends AbstractElementManipulator[HKey] {

  import org.jetbrains.plugins.hocon.lexer.HoconTokenType._

  def handleContentChange(key: HKey, range: TextRange, newContent: String): HKey = {
    val psiManager = PsiManager.getInstance(key.getProject)
    val allStringTypes = key.keyParts.map(_.stringType).toSet

    lazy val escapedContent =
      StringUtil.escapeStringCharacters(newContent)

    lazy val needsQuoting =
      newContent.isEmpty || newContent.startsWith(" ") || newContent.endsWith(" ") ||
        escapedContent != newContent || newContent.exists(HoconLexer.ForbiddenCharsAndDot.contains)

    val quotedEscapedContent =
      if (allStringTypes.contains(MultilineString))
        "\"\"\"" + newContent + "\"\"\""
      else if (allStringTypes.contains(QuotedString) || needsQuoting)
        "\"" + StringUtil.escapeStringCharacters(newContent) + "\""
      else
        newContent

    val newKey = HoconPsiElementFactory.createKey(quotedEscapedContent, psiManager)
    key.replace(newKey)
    newKey
  }
}
