package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api._


/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:48
*/

trait ScTypeAliasDeclarationBase extends ScTypeAliasBase with ScDeclarationBase { this: ScTypeAliasDeclaration =>
  override def declaredElements = Seq(this)

  override def isDefinition: Boolean = false
}