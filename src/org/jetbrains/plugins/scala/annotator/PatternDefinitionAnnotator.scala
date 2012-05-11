package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import lang.psi.api.base.types.ScSimpleTypeElement
import lang.psi.api.statements.ScPatternDefinition
import AnnotatorUtils._
import org.jetbrains.plugins.scala.extensions._

/**
 * Pavel.Fatin, 18.05.2010
 */

trait PatternDefinitionAnnotator {
  def annotatePatternDefinition(definition: ScPatternDefinition, holder: AnnotationHolder, highlightErrors: Boolean) {
    if (highlightErrors && definition.pList.allPatternsSimple) {
      for (expr <- definition.expr; element <- definition.children.findByType(classOf[ScSimpleTypeElement]))
        checkConformance(expr, element, holder)
    }
  }
}