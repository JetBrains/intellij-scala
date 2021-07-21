package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScPolyFunctionTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScPolyFunctionTypeElementImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
    with ScPolyFunctionTypeElement {

  override protected def innerType: TypeResult = {
    val resultType = this.flatMapType(resultTypeElement).getOrAny
    Right(ScTypePolymorphicType(resultType, typeParameters.map(TypeParameter(_))))
  }

  override def resultTypeElement: Option[ScTypeElement] = getLastChild.asOptionOf[ScTypeElement]
}

