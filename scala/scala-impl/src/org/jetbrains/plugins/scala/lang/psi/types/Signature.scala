package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.{PsiClass, PsiModifierListOwner, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

/**
  * Represents member of a class as seen from inheritor
  */
trait Signature {

  def namedElement: PsiNamedElement

  /**
    * Sometimes one element generate several signatures with different names.
    * Setter method for a `var` is a typical example.
    */
  def name: String

  def substitutor: ScSubstitutor

  def renamed: Option[String]

  def isAbstract: Boolean

  def isImplicit: Boolean

  def isSynthetic: Boolean

  def isExtensionMethod: Boolean = false

  def exportedIn: Option[PsiClass]

  def isPrivate: Boolean = namedElement match {
    case param: ScClassParameter if !param.isClassMember => true
    case inNameContext(s: ScModifierListOwner) =>
      s.getModifierList.accessModifier match {
        case Some(a: ScAccessModifier) => a.isUnqualifiedPrivateOrThis
        case _                         => false
      }
    case _: ScNamedElement       => false
    case n: PsiModifierListOwner => n.hasModifierPropertyScala("private")
    case _                       => false
  }

  //relation to use for building type hierarchy
  def equiv(other: Signature): Boolean

  def equivHashCode: Int

}

object Signature {
  def unapply(arg: Signature): Option[(PsiNamedElement, ScSubstitutor)] = Some((arg.namedElement, arg.substitutor))
}