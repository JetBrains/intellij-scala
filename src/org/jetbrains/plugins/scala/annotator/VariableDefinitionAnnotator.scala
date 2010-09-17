package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import lang.psi.api.statements.ScVariableDefinition
import lang.psi.api.base.types.ScSimpleTypeElement
/**
 * Pavel.Fatin, 18.05.2010
 */

trait VariableDefinitionAnnotator {
  def annotateVariableDefinition(declaration: ScVariableDefinition, holder: AnnotationHolder, highlightErrors: Boolean) {
    if (highlightErrors && declaration.pList.allPatternsSimple) {
      declaration.children.findByType(classOf[ScSimpleTypeElement]).foreach { element =>
        val expression = declaration.expr
        expression.getTypeAfterImplicitConversion().tr.foreach { actual =>
          val expected = element.calcType
          if (!actual.conforms(expected))
            holder.createErrorAnnotation(expression,
              "Type mismatch, found: %s, required: %s".format(actual.presentableText, expected.presentableText))
        }
      }
    }
  }
}