package org.jetbrains.plugins.scala.annotator.element

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Failure
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScMatchType, TypePresentationContext, extractTypeParameters}

object ScTypeBoundsOwnerAnnotator extends ElementAnnotator[ScTypeBoundsOwner] {

  override def annotate(element: ScTypeBoundsOwner, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    implicit val tcp: TypePresentationContext = element
    implicit val context: Context = Context(element)

    if (!typeAware) return

    val typeParamClause  = PsiTreeUtil.getParentOfType(element, classOf[ScTypeParamClause]).toOption
    val isFunctionClause = typeParamClause.flatMap(_.parent).exists(_.is[ScFunction])

    if (!isFunctionClause) {
      for {
        lower <- element.lowerBound.toOption
        upper <- element.upperBound.toOption
        // TODO: This is a dirty workaround fix for SCL-21814.
        //  We ignore match types at right hand side of type alias to avoid unuseful errors when CBH is disabled in Scala 3
        //  We still need to truly support match types (SCL-15104)
        //  Once the ticket is closed, remove this workaround filtering
        if !(element.is[ScTypeAliasDefinition] && lower.is[ScMatchType])
        if !lower.conforms(upper)
      } {
        holder.createErrorAnnotation(
          element,
          ScalaBundle.message("lower.bound.conform.to.upper", upper.presentableText, lower.presentableText)
        )
      }
    }

    element match {
      case ta: ScTypeAliasDefinition if ta.isOpaque =>
        for (at <- ta.aliasedType) {
          for (lowerElement <- ta.lowerTypeElement; lower <- lowerElement.`type`().toOption if !lower.conforms(at)) {
            holder.createErrorAnnotation(element, ScalaBundle.message("lower.bound.conform.to.upper", at.presentableText, lower.presentableText))
          }
          for (upperElement <- ta.upperTypeElement; upper <- upperElement.`type`().toOption if !at.conforms(upper)) {
            holder.createErrorAnnotation(element, ScalaBundle.message("lower.bound.conform.to.upper", upper.presentableText, at.presentableText))
          }
        }
      case _ =>
    }

    element.contextBounds.foreach { cb =>
      val cbTypeElem = cb.typeElement
      val cbType     = cbTypeElem.getTypeNoConstructor.toOption

      cbType.foreach { tpe =>
        ScParameterizedTypeElementAnnotator.annotateTypeArgs[PsiElement](
          extractTypeParameters(tpe),
          Seq(element.nameId),
          cbTypeElem.getTextRange,
          ScSubstitutor.empty,
          tpe.presentableText,
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
