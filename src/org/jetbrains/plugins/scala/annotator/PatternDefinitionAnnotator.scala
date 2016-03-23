package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem

/**
 * Pavel.Fatin, 18.05.2010
 */

trait PatternDefinitionAnnotator {
  def annotatePatternDefinition(definition: ScPatternDefinition, holder: AnnotationHolder, highlightErrors: Boolean)
                               (implicit typeSystem: TypeSystem = definition.typeSystem) {
    if (highlightErrors && definition.pList.allPatternsSimple) {
      for (expr <- definition.expr; element <- definition.children.findByType(classOf[ScSimpleTypeElement]))
        checkConformance(expr, element, holder)
    }
  }
}