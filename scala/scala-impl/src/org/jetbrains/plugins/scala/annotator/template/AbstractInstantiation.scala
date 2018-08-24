package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
  * Pavel Fatin
  */
object AbstractInstantiation extends AnnotatorPart[ScTemplateDefinition] {
  def THIS: AbstractInstantiation.type = this

  def annotate(definition: ScTemplateDefinition,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit = {
    val newObject = definition.isInstanceOf[ScNewTemplateDefinition]
    val hasBody = definition.extendsBlock.templateBody.isDefined
    val hasEarlyBody = definition.extendsBlock.earlyDefinitions.exists(_.members.nonEmpty)

    if (!newObject || hasEarlyBody || hasBody) return

    val refs = superRefs(definition)

    if (refs.size != 1) return

    refs.headOption.foreach {
      case (refElement, psiClass) if isAbstract(psiClass) =>
        holder.createErrorAnnotation(
          refElement,
          s"${kindOf(psiClass)} ${psiClass.name} is abstract; cannot be instantiated"
        )
      case _ =>
    }
  }
}