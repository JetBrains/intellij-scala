package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
  * Pavel Fatin
  */
object NeedsToBeTrait extends AnnotatorPart[ScTemplateDefinition] {

  def annotate(definition: ScTemplateDefinition,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit = {
    superRefs(definition).drop(1).collect {
      case (reference, clazz) if !isMixable(clazz) =>
        (reference, ScalaBundle.message("illegal.mixin", kindOf(clazz), clazz.name))
    }.foreach {
      case (reference, message) =>
        holder.createErrorAnnotation(reference, message)
    }
  }
}