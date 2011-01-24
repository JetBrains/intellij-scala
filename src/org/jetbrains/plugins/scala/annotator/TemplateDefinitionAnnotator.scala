package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import lang.psi.api.toplevel.typedef.ScTemplateDefinition
import lang.psi.api.base.ScReferenceElement

/**
 * Pavel Fatin
 */

trait TemplateDefinitionAnnotator {
  def annotateTemplateDefinition(defintion: ScTemplateDefinition, holder: AnnotationHolder) {
    val block = defintion.extendsBlock

    if(block.templateBody.isEmpty) return

    block.supers.filter(_.getModifierList.hasModifierProperty("final")).foreach { aClass =>
      defintion.depthFirst.filterByType(classOf[ScReferenceElement]).filter(_.getReference.isReferenceTo(aClass)).foreach { ref =>
        holder.createErrorAnnotation(ref, "Illegal inheritance from final class %s".format(aClass.getName))
      }
    }
  }
}
