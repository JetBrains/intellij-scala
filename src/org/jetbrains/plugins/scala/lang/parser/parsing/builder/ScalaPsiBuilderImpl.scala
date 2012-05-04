package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.PsiBuilder
import collection.mutable.Stack
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import com.intellij.lang.impl.PsiBuilderAdapter

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaPsiBuilderImpl(builder: PsiBuilder)
  extends PsiBuilderAdapter(builder) with ScalaPsiBuilder {
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
      val previousToken: IElementType = rawLookup(-i)
      previousToken match {
        case ScalaTokenTypes.tWHITE_SPACE_IN_LINE =>
          val previousTokenStart: Int = rawTokenTypeStart(-i)
          val previousTokenEnd: Int = rawTokenTypeStart(-i + 1)
          assert(previousTokenStart >= 0)
          assert(previousTokenEnd < getOriginalText.length)
          var j: Int = previousTokenStart
          while (j < previousTokenEnd) {
            if (getOriginalText.charAt(j) == '\n') res += 1
            j = j + 1
          }
        case ScalaTokenTypes.tLINE_COMMENT => res -= 1 //newline should be included to line comment
        case ScalaTokenTypes.tBLOCK_COMMENT | ScalaDocElementTypes.SCALA_DOC_COMMENT =>
        case _ => return res
      }
      i = i + 1
    }
    res
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