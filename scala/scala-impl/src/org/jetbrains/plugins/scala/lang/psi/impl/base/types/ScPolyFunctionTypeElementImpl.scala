package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{
  ScPolyFunctionTypeElement,
  ScTypeElement
}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPolyFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScPolyFunctionTypeElementImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
  with ScPolyFunctionTypeElement {

  override protected def innerType: TypeResult = {
    val contextBounds =
      typeParameters.flatMap {
        typeParam =>
          typeParam.contextBound.map(
            boundType => ScParameterizedType(boundType, Seq(TypeParameterType(typeParam)))
          )
      }

    val resultType = {
      val inner = this.flatMapType(resultTypeElement).getOrAny
      if (contextBounds.isEmpty)
        inner
      else
        ScPolyFunctionExpr.propagateContextBounds(inner, contextBounds)
    }
    resultType.removeAliasDefinitions()
    Right(ScTypePolymorphicType(resultType, typeParameters.map(TypeParameter(_))))
  }

  override def resultTypeElement: Option[ScTypeElement] = getLastChild.asOptionOf[ScTypeElement]
}
