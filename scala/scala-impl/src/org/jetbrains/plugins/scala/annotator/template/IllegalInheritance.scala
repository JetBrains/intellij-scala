package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt

/**
  * Pavel Fatin
  */

object IllegalInheritance extends AnnotatorPart[ScTemplateDefinition] {
  val Message = "Illegal inheritance, self-type %s does not conform to %s".format(_: String, _: String)

  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, typeAware: Boolean) {

    if (!typeAware) return

    definition.selfTypeElement.flatMap(_.`type`().toOption).
      orElse(definition.`type`().toOption).foreach { ownType =>

      collectSuperRefs(definition) {
        _.extractClassType
      }.foreach {
        case (refElement, (clazz: ScTemplateDefinition, subst)) =>
          clazz.selfType.filterNot { selfType =>
            ownType.conforms(subst.subst(selfType))
          }.foreach { selfType =>
            holder.createErrorAnnotation(
              refElement,
              Message(ownType.presentableText, selfType.presentableText)
            )
          }
        case _ =>
      }
    }
  }
}