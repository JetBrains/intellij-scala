package intellijhocon

import com.intellij.lang.{BracePair, PairedBraceMatcher}
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class HoconBraceMatcher extends PairedBraceMatcher {

  import HoconTokenType._

  def getPairs = Array(
    new BracePair(LBrace, RBrace, true),
    new BracePair(LBracket, RBracket, false),
    new BracePair(RefStart, RefEnd, false)
  )

  def isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType) =
    true

  def getCodeConstructStart(file: PsiFile, openingBraceOffset: Int) =
    openingBraceOffset
}
