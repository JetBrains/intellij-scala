package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, ToNullSafe, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPatternLike
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeVariableTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.PatternTypeInference
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScTypeVariableTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeVariableTypeElement {
  override protected def innerType: TypeResult =
    (
      for {
        pat         <- this.parentOfType[ScTypedPatternLike]
        typePattern <- pat.typePattern
        te          = typePattern.typeElement
        tpe         = te.unsubstitutedType.getOrAny
        expected    <- pat.expectedType
        subst       = PatternTypeInference.doTypeInference(pat, expected, tpe.toOption)
      } yield subst(tvType)
    ).asTypeResult

  private[this] lazy val tvType = TypeParameterType(TypeParameter.light(name, List.empty, Nothing, Any))

  override lazy val unsubstitutedType: TypeResult = Right(tvType)

  override def nameId: PsiElement =
    findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER).nullSafe
      .getOrElse(findChildByType[PsiElement](ScalaTokenTypes.tUNDER))

  override def toString: String = s"$typeName: ${ifReadAllowed(name)("")}"
}
