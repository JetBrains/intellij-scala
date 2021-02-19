package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeBoundsOwner, ScTypeBoundsOwnerBase}
/** 
* @author Alexander Podkhalyuzin
* Date: 11.04.2008
*/

trait ScWildcardTypeElementBase extends ScTypeElementBase with ScTypeBoundsOwnerBase { this: ScWildcardTypeElement =>
  override protected val typeName = "WildcardType"
}