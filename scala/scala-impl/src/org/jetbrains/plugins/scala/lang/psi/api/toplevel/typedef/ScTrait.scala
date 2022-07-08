package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.psi.PsiClass

trait ScTrait extends ScTypeDefinition with ScDerivesClauseOwner with ScConstructorOwner {

  def fakeCompanionClass: PsiClass
}