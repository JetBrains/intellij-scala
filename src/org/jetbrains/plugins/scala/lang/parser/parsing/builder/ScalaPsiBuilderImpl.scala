package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

import scala.collection.mutable.Stack

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
    val lines = s"start $textBefore end".split('\n')
    if (lines.exists(_.forall(StringUtil.isWhiteSpace))) 2
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