package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{ToNullSafe, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeVariableTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScTypeVariableTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeVariableTypeElement {
  private[this] lazy val tvType = TypeParameterType(TypeParameter.light(name, List.empty, Nothing, Any))

  override def innerType: TypeResult = Right(tvType)

  override def nameId: PsiElement =
    findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER).nullSafe
      .getOrElse(findChildByType[PsiElement](ScalaTokenTypes.tUNDER))

  override def toString: String = s"$typeName: ${ifReadAllowed(name)("")}"
}
