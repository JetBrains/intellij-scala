package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScTypeArgs extends ScArguments {
  def typeArgs: Seq[ScTypeElement]

  override def getArgsCount: Int = typeArgs.length
}