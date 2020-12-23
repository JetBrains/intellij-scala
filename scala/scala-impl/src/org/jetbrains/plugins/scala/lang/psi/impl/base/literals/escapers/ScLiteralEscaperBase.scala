package org.jetbrains.plugins.scala.lang.psi.impl.base.literals.escapers

import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral

abstract class ScLiteralEscaperBase[T <: ScStringLiteral](literal: T)
  extends LiteralTextEscaper[T](literal) {

  final val OutOfHostRange: Int = -1

  protected var outSourceOffsets: Array[Int] = _

  override final def getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int = {
    val offset =
      if (offsetInDecoded < outSourceOffsets.length) outSourceOffsets(offsetInDecoded)
      else OutOfHostRange

    if (offset == OutOfHostRange)
      OutOfHostRange
    else {
      val offsetFixed = offset.min(rangeInsideHost.getLength)
      offsetFixed + rangeInsideHost.getStartOffset
    }
  }
}
