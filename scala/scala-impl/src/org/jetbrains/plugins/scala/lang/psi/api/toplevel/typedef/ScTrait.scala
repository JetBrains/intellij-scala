package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.PsiClass

/** 
* @author Alexander Podkhalyuzin
* @since 20.02.2008
*/
trait ScTraitBase extends ScTypeDefinitionBase with ScConstructorOwnerBase { this: ScTrait =>

  def fakeCompanionClass: PsiClass
}