package org.jetbrains.plugins.scala.lang.psi.api.toplevel
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{tCOLON, tLOWER_BOUND, tUPPER_BOUND, tVIEW}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

import scala.annotation.unused

trait ScBoundsOwner extends ScTypeBoundsOwner with ScImplicitBoundsOwner {
  @unused("debug utility")
  def boundsText: String = {
    def toString(bounds: Iterable[ScTypeElement], elementType: IElementType) =
      bounds.map(e => s"${elementType.toString} ${e.getText}")

    (toString(lowerTypeElement, tLOWER_BOUND) ++
      toString(upperTypeElement, tUPPER_BOUND) ++
      toString(viewTypeElement, tVIEW) ++
      toString(contextBoundTypeElement, tCOLON))
      .mkString(" ")
  }
}
