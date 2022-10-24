package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package typedef

import com.intellij.psi.PsiClass

trait ScTrait extends ScTypeDefinition with ScDerivesClauseOwner with ScConstructorOwner {

  def fakeCompanionClass: PsiClass
}