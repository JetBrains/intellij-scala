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

trait ScTypeProjectionBase extends ScTypeElementBase with ScReferenceBase { this: ScTypeProjection =>
  override protected val typeName = "TypeProjection"

  def typeElement: ScTypeElement = findChild[ScTypeElement].get
}