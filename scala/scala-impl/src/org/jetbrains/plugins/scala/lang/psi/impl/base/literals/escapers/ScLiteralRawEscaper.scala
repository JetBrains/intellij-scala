package org.jetbrains.plugins.scala.lang.psi.impl.base.literals.escapers

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral

class ScLiteralRawEscaper(val literal: ScStringLiteral) extends ScLiteralEscaperBase[ScStringLiteral](literal) {

  override def decode(rangeInsideHost: TextRange, outChars: java.lang.StringBuilder): Boolean =
    decodeWithParser(rangeInsideHost, outChars, isRaw = true)
}
