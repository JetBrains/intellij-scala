package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.checkConformance
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.project.ProjectContext

// TODO remove the class?
object ScPatternDefinitionAnnotator extends ElementAnnotator[ScPatternDefinition] {
  override def annotate(element: ScPatternDefinition, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    implicit val ctx: ProjectContext = element

    if (typeAware && element.pList.simplePatterns) {
      val compiled = element.getContainingFile.asOptionOf[ScalaFile].exists(_.isCompiled)

      if (!compiled) {
        // Handled by ScExpressionAnnotator, don't add multiple errors
//        for (expr <- element.expr; element <- element.children.instanceOf[ScSimpleTypeElement])
//          checkConformance(expr, element, holder)
      }
    }
  }
}
