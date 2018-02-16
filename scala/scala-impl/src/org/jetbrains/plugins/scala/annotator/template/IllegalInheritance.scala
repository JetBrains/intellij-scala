package org.jetbrains.plugins.scala
package annotator.template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt

/**
 * Pavel Fatin
 */

object IllegalInheritance extends AnnotatorPart[ScTemplateDefinition] {
  val Message = "Illegal inheritance, self-type %s does not conform to %s".format(_: String, _: String)

  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, typeAware: Boolean) {

    if(!typeAware) return

    definition.selfTypeElement.flatMap(_.`type`().toOption).
      orElse(definition.`type`().toOption).foreach { ownType =>
      AnnotatorPart.superRefsWithSubst(definition).foreach {
        case (refElement, Some((SelfType(Some(aType)), subst)))  =>
          val anotherType = subst.subst(aType)
          if (!ownType.conforms(anotherType))
            holder.createErrorAnnotation(refElement, Message(ownType.presentableText, aType.presentableText))
        case _ =>
      }
    }
  }
}