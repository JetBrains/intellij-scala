package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
/** 
* @author Alexander Podkhalyuzin
* Date: 11.04.2008
*/

trait ScWildcardTypeElement extends ScTypeElement with ScTypeBoundsOwner {
  override protected val typeName = "WildcardType"
}