package org.jetbrains.plugins.scala.lang.psi.api.base
package types

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner

trait ScWildcardTypeElement extends ScTypeElement with ScTypeBoundsOwner {
  override protected val typeName = "WildcardType"
}