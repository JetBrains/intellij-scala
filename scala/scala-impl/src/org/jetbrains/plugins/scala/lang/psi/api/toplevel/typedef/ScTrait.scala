package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.psi.PsiClass

/**
* @author Alexander Podkhalyuzin
* @since 20.02.2008
*/
trait ScTrait extends ScTypeDefinition with ScDerivesClauseOwner with ScConstructorOwner {

  def fakeCompanionClass: PsiClass
}