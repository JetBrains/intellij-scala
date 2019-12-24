package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral

class ScLiteralEscaper(val literal: ScStringLiteral) extends LiteralTextEscaper[ScStringLiteral](literal) {
  private var outSourceOffsets: Array[Int] = _

  override def decode(rangeInsideHost: TextRange, outChars: java.lang.StringBuilder): Boolean = {
    TextRange.assertProperRange(rangeInsideHost)
    val subText = rangeInsideHost.substring(myHost.getText)
    outSourceOffsets = new Array[Int](subText.length + 1)
    PsiLiteralExpressionImpl.parseStringCharacters(subText, outChars, outSourceOffsets)
  }

  override def getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int = {
    val result =
      if (offsetInDecoded < outSourceOffsets.length) outSourceOffsets(offsetInDecoded)
      else -1

    if (result == -1) {
      -1
    } else {
      (if (result <= rangeInsideHost.getLength) result else rangeInsideHost.getLength) +
        rangeInsideHost.getStartOffset
    }
  }

  override def isOneLine: Boolean = {
    myHost.getValue match {
      case str: String => str.indexOf('\n') < 0
      case _ => false
    }
  }
}