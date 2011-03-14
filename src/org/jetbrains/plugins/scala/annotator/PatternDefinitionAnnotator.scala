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
  def annotatePatternDefinition(declaration: ScPatternDefinition, holder: AnnotationHolder, highlightErrors: Boolean) {
    if (highlightErrors && declaration.pList.allPatternsSimple) {
      for (element <- declaration.children.findByType(classOf[ScSimpleTypeElement]))
        checkConformance(declaration.expr, element, holder)
    }
  }
}