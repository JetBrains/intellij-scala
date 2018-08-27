package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
  * Pavel Fatin
  */
object NeedsToBeTrait extends TemplateDefinitionAnnotatorPart {

  def annotate(definition: ScTemplateDefinition,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit = superRefs(definition) match {
    case _ :: tail =>
      tail.collect {
        case (range, clazz) if !isMixable(clazz) =>
          (range, ScalaBundle.message("illegal.mixin", kindOf(clazz), clazz.name))
      }.foreach {
        case (range, message) =>
          holder.createErrorAnnotation(range, message)
      }
    case _ =>
  }
}