package org.jetbrains.plugins.scala.extensions.implementation

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern

/**
 * User: Alefas
 * Date: 16.02.12
 */


class PsiNamedElementExt(named: PsiNamedElement) {
  /**
   * Second match branch is for Java only.
   */
  def name: String = {
    named match {
      case named: ScNamedElement => named.name
      case named => named.getName
    }
  }
}

class PsiMemberExt(member: PsiMember) {
  /**
   * Second match branch is for Java only.
   */
  def containingClass: PsiClass = {
    member match {
      case member: ScMember => member.containingClass
      case b: ScBindingPattern => b.containingClass
      case _ => member.getContainingClass
    }
  }
}

class PsiModifierListOwnerExt(member: PsiModifierListOwner) {
  /**
   * Second match branch is for Java only.
   */
  def hasAbstractModifier: Boolean = {
    member match {
      case member: ScModifierListOwner => member.hasAbstractModifier
      case _ => member.hasModifierProperty(PsiModifier.ABSTRACT)
    }
  }

  /**
   * Second match branch is for Java only.
   */
  def hasFinalModifier: Boolean = {
    member match {
      case member: ScModifierListOwner => member.hasFinalModifier
      case _ => member.hasModifierProperty(PsiModifier.FINAL)
    }
  }

  /**
   * Second match branch is for Java only.
   */
  def hasModifierPropertyScala(name: String): Boolean = {
    member match {
      case member: ScModifierListOwner => member.hasModifierPropertyScala(name)
      case _ => member.hasModifierProperty(name)
    }
  }
}
