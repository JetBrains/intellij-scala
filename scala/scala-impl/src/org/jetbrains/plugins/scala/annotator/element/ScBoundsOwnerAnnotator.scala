package org.jetbrains.plugins.scala.annotator.element

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScBoundsOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{TypePresentationContext, extractTypeParameters}

object ScBoundsOwnerAnnotator extends ElementAnnotator[ScBoundsOwner] {

  override def annotate(element: ScBoundsOwner, typeAware: Boolean)
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

    element match {
      case tparam: ScTypeParam =>
        element.contextBoundTypeElement.foreach { cbTypeElem =>
          val cbType = cbTypeElem.getTypeNoConstructor.toOption
          implicit val tpc: TypePresentationContext = tparam
          cbType.foreach { tpe =>
              ScParameterizedTypeElementAnnotator.annotateTypeArgs[PsiElement](
                extractTypeParameters(tpe),
                Seq(tparam.nameId),
                cbTypeElem.getTextRange,
                ScSubstitutor.empty,
                tpe.presentableText(cbTypeElem),
                _ => Right(TypeParameterType(TypeParameter(tparam))),
                isForContextBound = true
              )
          }
        }
      case _ => ()
    }
  }
}
