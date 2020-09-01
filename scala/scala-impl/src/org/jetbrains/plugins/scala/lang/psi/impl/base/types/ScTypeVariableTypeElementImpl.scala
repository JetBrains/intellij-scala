package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeVariableTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiElementImpl}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.PatternTypeInferenceUtil
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScTypeVariableTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeVariableTypeElement {
  override protected def innerType: TypeResult = {
    var resTpe: Option[ScType] = None

    for {
      pat         <- this.parentOfType[ScTypedPattern]
      typePattern <- pat.typePattern
      te          = typePattern.typeElement
      newTe       = ScalaPsiElementFactory.createTypedPatternFromText(te.getText, te, null)
      tpe         = newTe.unsubstitutedType.getOrAny
      expected    <- pat.expectedType
      subst = PatternTypeInferenceUtil
        .doTypeInference(pat, expected, tpe.toOption)
        .getOrElse(ScSubstitutor.empty)
    } {
      tpe.visitRecursively {
        case tpt: TypeParameterType if tpt.name == this.name => resTpe = Option(subst(tpt))
        case _                                               => ()
      }
    }

    Right(resTpe.getOrElse(tvType))
  }

  private[this] val tvType = TypeParameterType(TypeParameter.light(name, List.empty, Nothing, Any))

  override val unsubstitutedType: TypeResult = Right(tvType)

  override def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)

  override def toString: String = s"$typeName: ${ifReadAllowed(name)("")}"
}
