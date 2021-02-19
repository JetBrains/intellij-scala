package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */
trait ScEarlyDefinitionsBase extends ScalaPsiElementBase { this: ScEarlyDefinitions =>
  def members: Seq[ScMember]
}