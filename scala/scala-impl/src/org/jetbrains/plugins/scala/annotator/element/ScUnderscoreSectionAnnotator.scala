package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderscoreSection
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScValueOrVariable, ScVariableDefinition}

object ScUnderscoreSectionAnnotator extends ElementAnnotator[ScUnderscoreSection] {

  override def annotate(element: ScUnderscoreSection, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    checkUnboundUnderscore(element)
    // TODO (otherwise there's no type conformance check)
    // super.visitUnderscoreExpression  }
  }

  private def checkUnboundUnderscore(under: ScUnderscoreSection)
                                    (implicit holder: ScalaAnnotationHolder): Unit = {
    if (under.textMatches("_")) {
      under.parentOfType(classOf[ScValueOrVariable], strict = false).foreach {
        case varDef @ ScVariableDefinition.expr(_) if varDef.expr.contains(under) =>
          if (varDef.containingClass == null) {
            val error = ScalaBundle.message("local.variables.must.be.initialized")
            holder.createErrorAnnotation(under, error)
          } else if (varDef.typeElement.isEmpty) {
            val error = ScalaBundle.message("unbound.placeholder.parameter")
            holder.createErrorAnnotation(under, error)
          } else if (varDef.typeElement.exists(_.is[ScLiteralTypeElement])) {
            holder.createErrorAnnotation(varDef.typeElement.get, ScalaBundle.message("default.init.prohibited.literal.types"))
          }
        case valDef @ ScPatternDefinition.expr(_) if valDef.expr.contains(under) =>
          holder.createErrorAnnotation(under, ScalaBundle.message("unbound.placeholder.parameter"))
        case _ =>
        // TODO SCL-2610 properly detect unbound placeholders, e.g. ( { _; (_: Int) } ) and report them.
        //  val error = ScalaBundle.message("unbound.placeholder.parameter")
        //  val annotation: Annotation = holder.createErrorAnnotation(under, error)
      }
    }
  }
}
