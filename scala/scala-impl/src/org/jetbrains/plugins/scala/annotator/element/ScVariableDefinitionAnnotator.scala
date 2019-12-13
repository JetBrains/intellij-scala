package org.jetbrains.plugins.scala
package annotator
package element

import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.checkConformance
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition

object ScVariableDefinitionAnnotator extends ElementAnnotator[ScVariableDefinition] {

  override def annotate(element: ScVariableDefinition, typeAware: Boolean = true)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (typeAware && element.pList.simplePatterns) {
      for {
        expr <- element.expr
        element <- element.children.instanceOf[ScSimpleTypeElement]
      } checkConformance(expr, element)
    }
  }
}
