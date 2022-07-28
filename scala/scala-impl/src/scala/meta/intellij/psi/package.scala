package scala.meta
package intellij

import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}

package object psi {

  object hasInlineAnnotation {

    def unapply(definition: ScTemplateDefinition): Boolean =
      definition.membersWithSynthetic.exists {
        case member if hasInlineModifier(member) => true
        case holder: ScAnnotationsHolder => holder.hasAnnotation("scala.meta.internal.inline.inline")
        case _ => false
      }

    private def hasInlineModifier(member: ScMember) =
      member.getModifierList.isInline
  }

}
