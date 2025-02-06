package org.jetbrains.plugins.scala.annotator.element

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Failure
import org.jetbrains.plugins.scala.lang.psi.types.{TypePresentationContext, extractTypeParameters}

object ScTypeBoundsOwnerAnnotator extends ElementAnnotator[ScTypeBoundsOwner] {

  override def annotate(element: ScTypeBoundsOwner, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (!typeAware) return

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
          _ => element match {
            case tparam: ScTypeParam => Right(TypeParameterType(tparam))
            case alias: ScTypeAlias =>
              val projection = for {
                containingClass <- alias.containingClass.toOption
                tpe             <- containingClass.getTypeWithProjections().toOption
              } yield ScProjectionType(tpe, alias)

              Right(projection.getOrElse(ScDesignatorType(alias)))
            case _ =>
              Failure(
                ScalaBundle.message("invalid.context.bounds.owner", element.getText)
              )(element)
          },
          isForContextBound = true
        )
      }
    }
  }
}
