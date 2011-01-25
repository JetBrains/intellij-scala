package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import lang.psi.api.toplevel.typedef.ScTemplateDefinition
import lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import lang.psi.api.expr.ScNewTemplateDefinition

/**
 * Pavel Fatin
 */

trait TemplateDefinitionAnnotator {
  def annotateTemplateDefinition(defintion: ScTemplateDefinition, holder: AnnotationHolder) {
    val block = defintion.extendsBlock

    if (defintion.isInstanceOf[ScNewTemplateDefinition] && block.templateBody.isEmpty) return

    block.templateParents.toSeq.flatMap(_.typeElements).foreach { refElement =>
      val classes = refElement.getType(TypingContext.empty).toOption.toSeq
              .flatMap(ScType.extractClass(_).toSeq)

      classes.filter(_.getModifierList.hasModifierProperty("final")).foreach { it =>
        holder.createErrorAnnotation(refElement, "Illegal inheritance from final class %s".format(it.getName))
      }

      classes.filter(_.getModifierList.hasModifierProperty("sealed"))
              .filter(_.getContainingFile != refElement.getContainingFile).foreach { it =>
        holder.createErrorAnnotation(refElement, "Illegal inheritance from sealed type %s".format(it.getName))
      }
    }
  }
}
