package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeVariableTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.NameId
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScTypeVariableTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeVariableTypeElement {
  private[this] lazy val tvType = TypeParameterType(TypeParameter.light(name, List.empty, Nothing, Any))

  override def innerType: TypeResult = Right(tvType)

  override def nameId: NameId = NameId.fromToken(findChildByType(TokenSets.ID_SET))

  override def toString: String = s"$typeName: ${ifReadAllowed(name)("")}"
}
