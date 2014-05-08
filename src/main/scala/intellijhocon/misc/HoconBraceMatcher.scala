package intellijhocon.misc

import com.intellij.lang.{BracePair, PairedBraceMatcher}
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import intellijhocon.lexer.{HoconTokenType, HoconTokenSets}

class HoconBraceMatcher extends PairedBraceMatcher {

  import HoconTokenType._
  import HoconTokenSets._

  def getPairs = Array(
    new BracePair(LBrace, RBrace, true),
    new BracePair(LBracket, RBracket, false),
    new BracePair(RefLBrace, RefRBrace, false)
  )

  private val AllowsPairedBraceBefore =
    WhitespaceOrComment | Comma | RBrace | RBracket

  def isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType) =
    AllowsPairedBraceBefore.contains(contextType)

  def getCodeConstructStart(file: PsiFile, openingBraceOffset: Int) =
    openingBraceOffset
}
