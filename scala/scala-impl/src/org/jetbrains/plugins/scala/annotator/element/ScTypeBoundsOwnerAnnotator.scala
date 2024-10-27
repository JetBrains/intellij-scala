package org.jetbrains.plugins.scala.annotator.element

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{TypePresentationContext, extractTypeParameters}

object ScTypeBoundsOwnerAnnotator extends ElementAnnotator[ScTypeBoundsOwner] {

  override def annotate(element: ScTypeBoundsOwner, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val typeParamClause  = PsiTreeUtil.getParentOfType(element, classOf[ScTypeParamClause]).toOption
    val isFunctionClause = typeParamClause.flatMap(_.parent).exists(_.is[ScFunction])

    if (!isFunctionClause) {
      for {
        lower <- element.lowerBound.toOption
        upper <- element.upperBound.toOption
        if !lower.conforms(upper)
      } {
        implicit val tcp: TypePresentationContext = element
        holder.createErrorAnnotation(
          element,
          ScalaBundle.message("lower.bound.conform.to.upper", upper.presentableText, lower.presentableText)
        )
      }
    }

    element.contextBounds.foreach { cb =>
      val cbTypeElem = cb.typeElement
      val cbType     = cbTypeElem.getTypeNoConstructor.toOption
      implicit val tpc: TypePresentationContext = element

      cbType.foreach { tpe =>
        ScParameterizedTypeElementAnnotator.annotateTypeArgs[PsiElement](
          extractTypeParameters(tpe),
          Seq(element.nameId),
          cbTypeElem.getTextRange,
          ScSubstitutor.empty,
          tpe.presentableText(cbTypeElem),
          _ => Right(
            TypeParameterType(
              TypeParameter.light(
                element.name,
                element.typeParameters.map(TypeParameter(_)),
                element.lowerBound.getOrNothing,
                element.upperBound.getOrAny
              )
            )
          ),
          isForContextBound = true
        )
      }
    }
  }
}
