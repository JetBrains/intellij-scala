package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */
trait ScEarlyDefinitions extends ScalaPsiElement {
  def members: Seq[ScMember]
}