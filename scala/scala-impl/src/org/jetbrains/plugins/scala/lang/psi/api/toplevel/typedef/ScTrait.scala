package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package typedef

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

trait ScTrait extends ScTypeDefinition with ScDerivesClauseOwner with ScConstructorOwner {

  def fakeCompanionClass: PsiClass

  override def injectedParentTraitConstructorCalls: collection.Set[(ScPrimaryConstructor, ScSubstitutor)] = Set.empty
}