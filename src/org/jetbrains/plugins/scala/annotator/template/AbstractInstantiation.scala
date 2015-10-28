package org.jetbrains.plugins.scala.annotator.template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * Pavel Fatin
 */

object AbstractInstantiation extends AnnotatorPart[ScTemplateDefinition] {
  def THIS = this

  def kind = classOf[ScTemplateDefinition]

  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, typeAware: Boolean) {
    val newObject = definition.isInstanceOf[ScNewTemplateDefinition]
    val hasBody = definition.extendsBlock.templateBody.isDefined
    val hasEarlyBody = definition.extendsBlock.earlyDefinitions.exists(_.members.nonEmpty)

    if(!newObject || hasEarlyBody || hasBody) return

    val refs = definition.refs

    if (refs.isEmpty) return
    if (refs.tail.nonEmpty) return

    refs.headOption.foreach {
      case (refElement, Some((psiClass, _))) if isAbstract(psiClass) =>
        holder.createErrorAnnotation(refElement,
          "%s %s is abstract; cannot be instantiated".format(kindOf(psiClass), psiClass.name))
      case _ =>
    }
  }
}