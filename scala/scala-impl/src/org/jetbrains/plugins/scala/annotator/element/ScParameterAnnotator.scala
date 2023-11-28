package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenDefinition
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

object ScParameterAnnotator extends ElementAnnotator[ScParameter] {
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

    element.owner match {
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
      case _: ScFunctionExpr =>
        if (element.typeElement.isEmpty && element.expectedParamType.isEmpty) {
          val inFunctionLiteral = element.parents.drop(2).nextOption().exists(_.is[ScFunctionExpr])
          if (!inFunctionLiteral) { // ScFunctionExprAnnotator does that more gracefully
            holder.createErrorAnnotation(element, ScalaBundle.message("missing.parameter.type.name", element.name))
          }
        }
    }
  }

  private def annotateCallByNameParameter(element: ScParameter)
                                         (implicit holder: ScalaAnnotationHolder): Any = {
    def errorWithMessageAbout(topic: String): Unit =
      holder.createErrorAnnotation(element, ScalaBundle.message("topic.parameters.may.not.be.call.by.name", topic))
    // TODO move to ScClassParameter
    element match {
      case cp: ScClassParameter if cp.isVal => errorWithMessageAbout("""'val'""")
      case cp: ScClassParameter if cp.isVar => errorWithMessageAbout("""'var'""")
      case cp: ScClassParameter if cp.isCaseClassVal => errorWithMessageAbout("case class")
      case p if p.isImplicitParameter && p.scalaLanguageLevel.forall(_ < ScalaLanguageLevel.Scala_2_13) =>
        errorWithMessageAbout("implicit")
      case _ =>
    }
  }
}
