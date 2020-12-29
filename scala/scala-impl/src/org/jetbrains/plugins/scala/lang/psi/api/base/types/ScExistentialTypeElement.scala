package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

trait ScExistentialTypeElement extends ScTypeElement {
  override protected val typeName = "ExistentialType"

  def quantified: ScTypeElement = findChild[ScTypeElement].get
  def clause: ScExistentialClause = findChild[ScExistentialClause].get
}