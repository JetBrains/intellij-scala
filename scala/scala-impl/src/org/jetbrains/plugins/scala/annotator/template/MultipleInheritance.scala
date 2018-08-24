package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
  * Pavel Fatin
  */
object MultipleInheritance extends AnnotatorPart[ScTemplateDefinition] {

  def annotate(definition: ScTemplateDefinition,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit = {
    superRefs(definition).groupBy(_._2).foreach {
      case (psiClass, entries) if isMixable(psiClass) && entries.size > 1 =>
        entries.map(_._1).foreach { refElement =>
          holder.createErrorAnnotation(
            refElement,
            s"${kindOf(psiClass)} ${psiClass.name} inherited multiple times"
          )
        }
      case _ =>
    }
  }
}