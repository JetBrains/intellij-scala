package org.jetbrains.plugins.scala.lang.psi.impl.base

import java.lang

import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper

class PassthroughLiteralEscaper(val literal: ScLiteralImpl) extends LiteralTextEscaper[ScLiteralImpl](literal) {
  override def decode(rangeInsideHost: TextRange, outChars: lang.StringBuilder): Boolean = {
    TextRange.assertProperRange(rangeInsideHost)
    outChars.append(myHost.getText, rangeInsideHost.getStartOffset, rangeInsideHost.getEndOffset)
    true
  }

  override def getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int = {
    var offset = offsetInDecoded + rangeInsideHost.getStartOffset
    if (offset < rangeInsideHost.getStartOffset) offset = rangeInsideHost.getStartOffset
    if (offset > rangeInsideHost.getEndOffset) offset = rangeInsideHost.getEndOffset
    offset
  }

  override def isOneLine: Boolean = {
    myHost.getValue match {
      case str: String => str.indexOf('\n') < 0
      case _ => false
    }
  }
}