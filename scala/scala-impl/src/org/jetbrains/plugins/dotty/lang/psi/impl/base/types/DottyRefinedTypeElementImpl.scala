package org.jetbrains.plugins.dotty.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.dotty.lang.psi.api.base.types.DottyRefinedTypeElement
import org.jetbrains.plugins.dotty.lang.psi.types.DottyRefinedType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElementExt
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

/**
  * @author adkozlov
  */
class DottyRefinedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with DottyRefinedTypeElement {
  override protected def innerType: TypeResult[ScType] =
    this.success(DottyRefinedType(typeElement.getType().getOrAny, refinement))
}
