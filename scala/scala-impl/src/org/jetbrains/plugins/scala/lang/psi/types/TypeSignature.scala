package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.ScExportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * Represents type alias, inner class or trait.
 */
case class TypeSignature(
  override val namedElement: PsiNamedElement,
  override val substitutor:  ScSubstitutor,
  override val renamed:      Option[String] = None,
  override val exportedIn:   Option[ScExportsHolder] = None
) extends Signature {
  override val name: String = ScalaNamesUtil.clean(renamed.getOrElse(namedElement.name))

  override def isAbstract: Boolean = namedElement match {
    case _: ScTypeAliasDeclaration => true
    case _                         => false
  }

  override def isImplicit: Boolean = false

  override def isSynthetic: Boolean = false

  override def equiv(other: Signature): Boolean = name == other.name

  override def equivHashCode: Int = name.hashCode

  override def equals(other: Any): Boolean = other match {
    case that: TypeSignature => namedElement == that.namedElement
    case _                   => false
  }

  override def hashCode(): Int = namedElement.hashCode()
}