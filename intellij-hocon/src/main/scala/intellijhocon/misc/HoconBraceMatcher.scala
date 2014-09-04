package intellijhocon
package misc

import com.intellij.lang.{BracePair, PairedBraceMatcher}
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import intellijhocon.lexer.{HoconTokenSets, HoconTokenType}

class HoconBraceMatcher extends PairedBraceMatcher {

  import intellijhocon.Util._
  import intellijhocon.lexer.HoconTokenSets._
  import intellijhocon.lexer.HoconTokenType._

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
