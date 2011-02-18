package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition

/**
 * Pavel Fatin
 */

object AbstractInstantiation extends AnnotatorPart[ScTemplateDefinition] {
  def kind = classOf[ScTemplateDefinition]

  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, advanced: Boolean) {
    val newObject = definition.isInstanceOf[ScNewTemplateDefinition]
    val hasBody = definition.extendsBlock.templateBody.isDefined

    if(!newObject || hasBody) return

    val refs = definition.refs

    if(!refs.tail.isEmpty) return

    refs.headOption.foreach {
      case (refElement, Some(psiClass)) if isAbstract(psiClass) => {
          holder.createErrorAnnotation(refElement,
            "%s %s is abstract; cannot be instantiated".format(kindOf(psiClass), psiClass.getName))
      }
      case _ =>
    }
  }
}