package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.checkConformance
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.extensions._


trait ScVariableDefinitionAnnotator extends Annotatable { self: ScVariableDefinition =>

  override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    if (typeAware && pList.simplePatterns) {
      for (expr <- expr; element <- self.children.instanceOf[ScSimpleTypeElement])
        checkConformance(expr, element, holder)
    }
  }
}
