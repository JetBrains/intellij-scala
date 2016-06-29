package org.jetbrains.plugins.hocon.misc

import com.intellij.lang.{BracePair, PairedBraceMatcher}
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class HoconBraceMatcher extends PairedBraceMatcher {

  import org.jetbrains.plugins.hocon.CommonUtil._
  import org.jetbrains.plugins.hocon.lexer.HoconTokenSets._
  import org.jetbrains.plugins.hocon.lexer.HoconTokenType._

  def getPairs = Array(
    new BracePair(LBrace, RBrace, true),
    new BracePair(LBracket, RBracket, false),
    new BracePair(SubLBrace, SubRBrace, false)
  )

  private val AllowsPairedBraceBefore =
    WhitespaceOrComment | Comma | RBrace | RBracket

  def isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType): Boolean =
    AllowsPairedBraceBefore.contains(contextType)

  def getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int =
    openingBraceOffset
}
