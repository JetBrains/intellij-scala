package org.jetbrains.plugins.scala.lang.psi

import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiModifierListOwnerExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}

package object impl {
  // a check to see if an overridable member can be overridden
  // This method does not apply to classes or things that are inherited
  private[impl] def canNotBeOverridden(member: ScMember): Boolean = {
    assert(!member.isInstanceOf[ScTemplateDefinition])
    member.hasFinalModifier ||
      member.isLocal ||
      member.isTopLevel ||
      member.isPrivate ||
      Option(member.containingClass).exists(_.isEffectivelyFinal)
  }
}
