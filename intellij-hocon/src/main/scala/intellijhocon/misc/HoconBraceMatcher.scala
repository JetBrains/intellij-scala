package intellijhocon
package misc

import com.intellij.lang.{BracePair, PairedBraceMatcher}
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import intellijhocon.lexer.{HoconTokenType, HoconTokenSets}
import intellijhocon.Util

class HoconBraceMatcher extends PairedBraceMatcher {

  import HoconTokenType._
  import HoconTokenSets._
  import Util._

  def getPairs = Array(
    new BracePair(LBrace, RBrace, true),
    new BracePair(LBracket, RBracket, false),
    new BracePair(SubLBrace, SubRBrace, false)
  )

  private val AllowsPairedBraceBefore =
    WhitespaceOrComment | Comma | RBrace | RBracket

  def isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType) =
    AllowsPairedBraceBefore.contains(contextType)

  def getCodeConstructStart(file: PsiFile, openingBraceOffset: Int) =
    openingBraceOffset
}
