package org.jetbrains.plugins.scala
package annotator
package element

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.project.ProjectContext

// TODO remove the class?
object ScPatternDefinitionAnnotator extends ElementAnnotator[ScPatternDefinition] {

  override def annotate(element: ScPatternDefinition, typeAware: Boolean = true)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
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
