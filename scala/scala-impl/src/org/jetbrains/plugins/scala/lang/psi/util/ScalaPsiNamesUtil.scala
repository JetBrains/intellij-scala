package org.jetbrains.plugins.scala.lang.psi.util

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

/**
 * PSI-based utilities for Scala names.
 *
 * @see [[org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil]] for string-level ones
 */
object ScalaPsiNamesUtil {

  def scalaName(element: PsiElement): String = element match {
    case scNamed: ScNamedElement => scNamed.name
    case psiNamed: PsiNamedElement => psiNamed.getName
  }

  def qualifiedName(named: PsiNamedElement): Option[String] =
    named.nameContext match {
      case pack: PsiPackage => Some(pack.getQualifiedName)
      case ClassQualifiedName(qualifiedName) => Some(qualifiedName)
      case member: PsiMember if !member.hasModifierProperty(PsiModifier.STATIC) => None
      case ContainingClass(ClassQualifiedName(qualifiedName)) =>
        ScalaPsiNamesUtil.qualifiedName(named, qualifiedName)
      case TopLevelMember(_, packaging) =>
        qualifiedName(named, packaging.fqn)
      case _ => None
    }

  private def qualifiedName(named: PsiNamedElement, ownerFqn: String): Option[String] =
    Option.when(ownerFqn.nonEmpty) {
      named.name match {
        case null | "" => ownerFqn
        case name => ownerFqn + "." + name
      }
    }
}
