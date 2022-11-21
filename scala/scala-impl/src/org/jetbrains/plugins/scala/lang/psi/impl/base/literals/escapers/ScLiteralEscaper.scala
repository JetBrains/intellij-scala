package org.jetbrains.plugins.scala.lang.psi.impl.base.literals.escapers

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral

class ScLiteralEscaper(val literal: ScStringLiteral) extends ScLiteralEscaperBase[ScStringLiteral](literal) {

  override def decode(rangeInsideHost: TextRange, outChars: java.lang.StringBuilder): Boolean = {
    TextRange.assertProperRange(rangeInsideHost)

    val chars = myHost.getText.substring(rangeInsideHost.getStartOffset, rangeInsideHost.getEndOffset)
    outSourceOffsets = new Array[Int](chars.length + 1)

    val parser = new ScalaStringParser(
      outSourceOffsets,
      isRaw = false
    )
    parser.parse(chars, outChars)
  }
}