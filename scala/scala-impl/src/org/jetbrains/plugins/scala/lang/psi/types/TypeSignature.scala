package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * Represents type alias, inner class or trait.
  */
class TypeSignature(val namedElement: PsiNamedElement, val substitutor: ScSubstitutor)
  extends Signature {

  val name: String = ScalaNamesUtil.clean(namedElement.name)

  def isAbstract: Boolean = namedElement match {
    case _: ScTypeAliasDeclaration => true
    case _ => false
  }

  def isImplicit: Boolean = false

  def isSynthetic: Boolean = false

  def equiv(other: Signature): Boolean = name == other.name

  def equivHashCode: Int = name.hashCode
}

object TypeSignature {
  def apply(namedElement: PsiNamedElement, scSubstitutor: ScSubstitutor) =
    new TypeSignature(namedElement, scSubstitutor)
}

