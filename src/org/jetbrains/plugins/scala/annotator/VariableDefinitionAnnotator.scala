package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import lang.psi.api.statements.ScVariableDefinition
import lang.psi.api.base.types.ScSimpleTypeElement
import AnnotatorUtils._
import org.jetbrains.plugins.scala.extensions._

/**
 * Pavel.Fatin, 18.05.2010
 */
trait VariableDefinitionAnnotator {
  def annotateVariableDefinition(definition: ScVariableDefinition, holder: AnnotationHolder, highlightErrors: Boolean) {
    if (highlightErrors && definition.pList.allPatternsSimple) {
      for (expr <- definition.expr; element <- definition.children.findByType(classOf[ScSimpleTypeElement]))
        checkConformance(expr, element, holder)
    }
  }
}