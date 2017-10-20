package org.jetbrains.plugins.dotty.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs

/**
  * @author adkozlov
  */
trait DottyTypeArgs extends ScTypeArgs {
  def argumentsNames: Seq[DottyTypeArgumentNameElement]
}
