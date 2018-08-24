package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
  * Pavel Fatin
  */
object NeedsToBeTrait extends AnnotatorPart[ScTemplateDefinition] {

  def annotate(definition: ScTemplateDefinition,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit = {
    superRefs(definition).drop(1).foreach {
      case (refElement, psiClass) if !isMixable(psiClass) =>
        holder.createErrorAnnotation(
          refElement,
          s"${kindOf(psiClass)} ${psiClass.name} needs to be trait to be mixed in"
        )
      case _ =>
    }
  }
}