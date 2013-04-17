package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilder
import collection.mutable.Stack
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import com.intellij.lang.impl.PsiBuilderAdapter
import scala.annotation.tailrec
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.lang.TokenSets

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaPsiBuilderImpl(builder: PsiBuilder)
  extends PsiBuilderAdapter(builder) with ScalaPsiBuilder {
  private final val newlinesEnabled: Stack[Boolean] = new Stack[Boolean]

  def newlineBeforeCurrentToken: Boolean = {
    countNewlineBeforeCurrentToken() > 0
  }

  def twoNewlinesBeforeCurrentToken: Boolean = {
    countNewlineBeforeCurrentToken() > 1
  }

  /**
   * @return 0 if new line is disabled here, or there is no \n chars between tokens
   *         1 if there is no blank lines between tokens
   *         2 otherwise
   */
  private def countNewlineBeforeCurrentToken(): Int = {
    if (!newlinesEnabled.isEmpty && !newlinesEnabled.top) return 0
    if (eof) return 0
    if (!ParserUtils.elementCanStartStatement(getTokenType, this)) return 0

    var i = 1
    while (i < getCurrentOffset && TokenSets.WHITESPACE_OR_COMMENT_SET.contains(rawLookup(-i))) i += 1
    val textBefore = getOriginalText.subSequence(rawTokenTypeStart(-i + 1), rawTokenTypeStart(0)).toString
    if (!textBefore.contains('\n')) return 0
    val lines = s"start $textBefore end".filter(_ != '\r').split('\n')
    if (lines.find(_.forall(StringUtil.isWhiteSpace)).isDefined) 2
    else 1
  }

  def disableNewlines {
    newlinesEnabled.push(false)
  }

  def enableNewlines {
    newlinesEnabled.push(true)
  }

  def restoreNewlinesState {
    assert(newlinesEnabled.size >= 1)
    newlinesEnabled.pop()
  }
}