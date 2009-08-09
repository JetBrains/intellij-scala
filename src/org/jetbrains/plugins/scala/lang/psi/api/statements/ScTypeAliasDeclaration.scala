package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:48
*/

trait ScTypeAliasDeclaration extends ScTypeAlias with ScDeclaration {
  def declaredElements = Seq.singleton(this)
}