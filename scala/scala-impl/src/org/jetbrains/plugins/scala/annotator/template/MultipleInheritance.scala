package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
  * Pavel Fatin
  */
object MultipleInheritance extends TemplateDefinitionAnnotatorPart {

  def annotate(definition: ScTemplateDefinition,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit = {
    superRefs(definition).groupBy(_._2).flatMap {
      case (clazz, entries) if isMixable(clazz) && entries.size > 1 => entries.map {
        case (range, _) => (range, ScalaBundle.message("illegal.inheritance.multiple", kindOf(clazz), clazz.name))
      }
      case _ => Seq.empty
    }.foreach {
      case (range, message) =>
        holder.createErrorAnnotation(range, message)
    }
  }
}