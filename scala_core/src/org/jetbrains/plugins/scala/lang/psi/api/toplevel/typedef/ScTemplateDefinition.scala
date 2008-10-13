package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.psi.{PsiClass, PsiNamedElement}
import statements.{ScFunction, ScTypeAlias}
import types.{ScType, PhysicalSignature, ScSubstitutor}

/**
 * Created by IntelliJ IDEA.
 * User: test
 * Date: Oct 14, 2008
 * Time: 12:23:38 AM
 * To change this template use File | Settings | File Templates.
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