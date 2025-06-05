package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.{ScalaAnnotationHolder, isDumbMode}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenDefinition
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

object ScParameterAnnotator extends ElementAnnotator[ScParameter] with DumbAware {
  private val isVarOrVal = Set(ScalaTokenTypes.kVAR, ScalaTokenTypes.kVAL)

  override def annotate(element: ScParameter, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (!element.is[ScClassParameter]) {
      for {
        child <- element.children
        if isVarOrVal(child.getNode.getElementType)
      } {
        holder.createErrorAnnotation(
          child,
          ScalaBundle.message("val.or.var.can.only.be.used.in.class.parameters", child.getText),
          ProblemHighlightType.GENERIC_ERROR,
        )
      }
    }

    val owner = element.owner

    annotateInlineParameter(element, owner)

    owner match {
      case null =>
        val message = ScalaBundle.message("annotator.error.parameter.without.an.owner.name", element.name)
        holder.createErrorAnnotation(element, message)
      case _: ScGivenDefinition =>
        if (element.typeElement.isEmpty) {
          val message = ScalaBundle.message("annotator.error.missing.type.annotation.for.parameter", element.name)
          holder.createErrorAnnotation(element, message)
        }
        if (element.isCallByNameParameter)
          annotateCallByNameParameter(element)
      case _: ScMethodLike | _: ScExtension =>
        if (element.isCallByNameParameter)
          annotateCallByNameParameter(element)
      case _: ScFunctionExpr if !isDumbMode(element.getProject) =>
        if (element.typeElement.isEmpty && element.expectedParamType.isEmpty) {
          val inFunctionLiteral = element.parents.drop(2).nextOption().exists(_.is[ScFunctionExpr])
          if (!inFunctionLiteral) { // ScFunctionExprAnnotator does that more gracefully
            holder.createErrorAnnotation(element, ScalaBundle.message("missing.parameter.type.name", element.name))
          }
        }
      case _ =>
    }
  }

  private def annotateInlineParameter(
    param: ScParameter,
    owner: PsiElement
  )(implicit holder: ScalaAnnotationHolder): Unit =
    if (param.getModifierList.isInline)
      owner match {
        case fn: ScFunction if fn.getModifierList.isInline => ()
        case _ =>
          val inlineMod = param.getModifierList.findChildrenByType(ScalaTokenType.InlineKeyword)

          inlineMod.foreach(
            holder.createErrorAnnotation(
              _,
              ScalaBundle.message("inline.modifier.illegal.owner")
            )
          )
      }

  private def annotateCallByNameParameter(element: ScParameter)
                                         (implicit holder: ScalaAnnotationHolder): Unit = {
    def errorWithMessageAbout(topic: String): Unit =
      holder.createErrorAnnotation(element, ScalaBundle.message("topic.parameters.may.not.be.call.by.name", topic))
    // TODO move to ScClassParameter
    element match {
      case cp: ScClassParameter if cp.isVal => errorWithMessageAbout("""'val'""")
      case cp: ScClassParameter if cp.isVar => errorWithMessageAbout("""'var'""")
      case cp: ScClassParameter if cp.isCaseClassPrimaryParameter => errorWithMessageAbout("case class")
      case p if p.isInClauseWithImplicit && p.scalaLanguageLevel.forall(_ < ScalaLanguageLevel.Scala_2_13) =>
        errorWithMessageAbout("implicit")
      case _ =>
    }
  }
}
