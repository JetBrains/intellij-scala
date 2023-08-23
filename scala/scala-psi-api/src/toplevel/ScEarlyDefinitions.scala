package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

trait ScEarlyDefinitions extends ScalaPsiElement {
  def members: Seq[ScMember]
}