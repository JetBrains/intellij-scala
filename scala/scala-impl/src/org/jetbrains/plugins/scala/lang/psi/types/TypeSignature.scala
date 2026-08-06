package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration
import org.jetbrains.plugins.scala.lang.psi.types.Signature.ExportedSigInfo
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * [[Signature]] specialization for type-level members.
 *
 * In Scala, "type-level" means names from the type namespace:
 * members referenced in type positions rather than expression positions.
 *
 * Example:
 * {{{
 *   trait A {
 *     type Out = Int
 *     class Inner
 *   }
 *
 *   val x: A#Out = 1
 *   def mk(a: A): a.Inner = new a.Inner
 * }}}
 *
 * Here type signatures are created for `Out` and `Inner`.
 *
 * Represents type aliases, nested type definitions.<br>
 * Unlike [[TermSignature]], it models type-member matching by visible type-member name.
 */
case class TypeSignature(
  override val namedElement: PsiNamedElement,
  override val substitutor:  ScSubstitutor,
  override val renamed:      Option[String]          = None,
  override val exportedInfo: Option[ExportedSigInfo] = None
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
