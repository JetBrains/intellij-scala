package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilder
import collection.mutable.Stack
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import com.intellij.lang.impl.PsiBuilderAdapter
import scala.annotation.tailrec

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaPsiBuilderImpl(builder: PsiBuilder)
  extends PsiBuilderAdapter(builder) with ScalaPsiBuilder {
  private final val newlinesEnabled: Stack[Boolean] = new Stack[Boolean]

  def newlineBeforeCurrentToken: Boolean = {
    countNewlineBeforeCurrentToken(countLineComments = false) > 0
  }

  def twoNewlinesBeforeCurrentToken: Boolean = {
    countNewlineBeforeCurrentToken(countLineComments = true) > 1
  }

  /**
   * @param countLineComments means not to include line breaks which belongs to line comments
   */
  private def countNewlineBeforeCurrentToken(countLineComments: Boolean): Int = {
    if (!newlinesEnabled.isEmpty && !newlinesEnabled.top) return 0
    if (eof) return 0
    if (!ParserUtils.elementCanStartStatement(getTokenType, this)) return 0

    val lineCommentsSubtrahend = if (countLineComments) 1 else 0

    @tailrec
    def go(i: Int, res: Int = 0, afterBlockComment: Boolean = false): Int = {
      if (i > getCurrentOffset) return res
      val previousToken: IElementType = rawLookup(-i)
      previousToken match {
        case ScalaTokenTypes.tWHITE_SPACE_IN_LINE =>
          val previousTokenStart: Int = rawTokenTypeStart(-i)
          val previousTokenEnd: Int = rawTokenTypeStart(-i + 1)
          assert(previousTokenStart >= 0)
          assert(previousTokenEnd < getOriginalText.length)
          var j: Int = previousTokenStart
          var add: Int = 0
          while (j < previousTokenEnd) {
            if (getOriginalText.charAt(j) == '\n') add += 1
            j = j + 1
          }
          if (add > 0 && afterBlockComment) add -= lineCommentsSubtrahend
          go(i + 1, res + add)
        case ScalaTokenTypes.tLINE_COMMENT =>
          go(i + 1, res - lineCommentsSubtrahend)
        case ScalaTokenTypes.tBLOCK_COMMENT | ScalaDocElementTypes.SCALA_DOC_COMMENT => go(i + 1, res, afterBlockComment = true)
        case _ => res
      }
    }
    go(1).max(0)
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