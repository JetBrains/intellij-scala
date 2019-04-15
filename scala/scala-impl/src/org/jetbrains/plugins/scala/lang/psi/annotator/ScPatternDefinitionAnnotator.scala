package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.checkConformance
import org.jetbrains.plugins.scala.lang.psi.api.{Annotatable, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.extensions._

trait ScPatternDefinitionAnnotator extends Annotatable { self: ScPatternDefinition =>

  override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    implicit val ctx: ProjectContext = this

    if (typeAware && pList.simplePatterns) {
      val compiled = getContainingFile.asOptionOf[ScalaFile].exists(_.isCompiled)

      if (!compiled) {
        for (expr <- expr; element <- self.children.instanceOf[ScSimpleTypeElement])
          checkConformance(expr, element, holder)
      }
    }
  }
}
