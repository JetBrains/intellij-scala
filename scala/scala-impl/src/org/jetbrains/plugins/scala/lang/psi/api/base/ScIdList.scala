package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.api._


/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScIdListBase extends ScalaPsiElementBase { this: ScIdList =>
  def fieldIds: Seq[ScFieldId]
}