package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.psi.{PsiClass, PsiNamedElement}
import statements.{ScFunction, ScTypeAlias}
import types.{ScType, PhysicalSignature, ScSubstitutor}

/**
 * @author ven
 */
trait ScTemplateDefinition extends ScNamedElement with PsiClass {
  def members(): Seq[ScMember]

  def functions(): Seq[ScFunction]

  def aliases(): Seq[ScTypeAlias]

  def typeDefinitions(): Seq[ScTypeDefinition]

  def superTypes(): Seq[ScType]

  def allTypes(): Iterator[Pair[PsiNamedElement, ScSubstitutor]]
  def allVals(): Iterator[Pair[PsiNamedElement, ScSubstitutor]]
  def allMethods(): Iterator[PhysicalSignature]
}