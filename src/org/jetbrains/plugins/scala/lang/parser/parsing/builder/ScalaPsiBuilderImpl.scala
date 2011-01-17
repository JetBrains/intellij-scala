package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.java.parser.JavaParserUtil
import com.intellij.lang.PsiBuilder
import collection.mutable.Stack
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaPsiBuilderImpl(builder: PsiBuilder)
  extends JavaParserUtil.PsiBuilderAdapter(builder) with ScalaPsiBuilder {
  private final val newlinesEnabled: Stack[Boolean] = new Stack[Boolean]

  def newlineBeforeCurrentToken: Boolean = {
    countNewlineBeforeCurrentToken > 0
  }

  def countNewlineBeforeCurrentToken: Int = {
    if (!newlinesEnabled.isEmpty && !newlinesEnabled.top) return 0
    if (eof) return 0
    if (!ParserUtils.elementCanStartStatement(getTokenType, this)) return 0

    var res: Int = 0
    var i: Int = 1
    while (i <= getCurrentOffset) {
      var previousToken: IElementType = rawLookup(-i)
      if (previousToken != ScalaTokenTypes.tWHITE_SPACE_IN_LINE &&
        !(previousToken == ScalaTokenTypes.tBLOCK_COMMENT || previousToken == ScalaDocElementTypes.SCALA_DOC_COMMENT)) {
        return res
      }
      val previousTokenStart: Int = rawTokenTypeStart(-i)
      val previousTokenEnd: Int = rawTokenTypeStart(-i + 1)
      assert(previousTokenStart >= 0)
      assert(previousTokenEnd < getOriginalText.length)
      var j: Int = previousTokenStart
      while (j < previousTokenEnd) {
        if (getOriginalText.charAt(j) == '\n') res += 1
        j = j + 1
      }
      i = i + 1
    }
    return res
  }

  def disableNewlines: Unit = {
    newlinesEnabled.push(false)
  }

  def enableNewlines: Unit = {
    newlinesEnabled.push(true)
  }

  def restoreNewlinesState: Unit = {
    assert(newlinesEnabled.size >= 1)
    newlinesEnabled.pop
  }
}