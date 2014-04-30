package intellijhocon

import com.intellij.lang.{BracePair, PairedBraceMatcher}
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class HoconBraceMatcher extends PairedBraceMatcher {

  import HoconTokenType._

  def getPairs = Array(
    new BracePair(LBrace, RBrace, true),
    new BracePair(LBracket, RBracket, false),
    new BracePair(RefLBrace, RefRBrace, false)
  )

  def isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType) =
    contextType match {
      case Whitespace | HashComment | NewLine | Comma | RBrace | RBracket => true
      case _ => false
    }

  def getCodeConstructStart(file: PsiFile, openingBraceOffset: Int) =
    openingBraceOffset
}
