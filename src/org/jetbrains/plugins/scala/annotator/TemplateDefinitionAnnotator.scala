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

    block.templateParents.toSeq.flatMap(_.typeElements).foreach { element =>
      element.getType(TypingContext.empty).toOption.toSeq
              .flatMap(ScType.extractClass(_).toSeq)
              .filter(_.getModifierList.hasModifierProperty("final")).foreach { aClass =>
        holder.createErrorAnnotation(element, "Illegal inheritance from final class %s".format(aClass.getName))
      }
    }
  }
}
