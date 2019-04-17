package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.checkConformance
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition

object ScVariableDefinitionAnnotator extends ElementAnnotator[ScVariableDefinition] {
  override def annotate(element: ScVariableDefinition, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    if (typeAware && element.pList.simplePatterns) {
      for (expr <- element.expr; element <- element.children.instanceOf[ScSimpleTypeElement])
        checkConformance(expr, element, holder)
    }
  }
}
