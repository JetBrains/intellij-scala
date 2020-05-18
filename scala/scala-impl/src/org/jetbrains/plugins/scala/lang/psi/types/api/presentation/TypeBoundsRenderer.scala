package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiPresentationUtils.TypeRenderer
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

final class TypeBoundsRenderer(
  textEscaper: TextEscaper = TextEscaper.Noop
) {

  import ScalaTokenTypes.{tLOWER_BOUND, tUPPER_BOUND}

  def upperBoundText(typ: ScType)
                    (typeRenderer: TypeRenderer): String =
    if (typ.isAny) ""
    else boundText(typ, tUPPER_BOUND)(typeRenderer)

  def lowerBoundText(typ: ScType)
                    (typeRenderer: TypeRenderer): String =
    if (typ.isNothing) ""
    else boundText(typ, tLOWER_BOUND)(typeRenderer)

  def boundText(typ: ScType, bound: IElementType)
               (toString: TypeRenderer): String = {
    val boundEscaped = textEscaper.escape(bound.toString)
    " " + boundEscaped + " " + toString(typ)
  }
}