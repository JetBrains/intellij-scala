package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api._


/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

trait ScExistentialTypeElementBase extends ScTypeElementBase { this: ScExistentialTypeElement =>
  override protected val typeName = "ExistentialType"

  def quantified: ScTypeElement = findChild[ScTypeElement].get
  def clause: ScExistentialClause = findChild[ScExistentialClause].get
}