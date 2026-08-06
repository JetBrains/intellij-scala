package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.ScExportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScOptionalBracesOwner

trait ScExtensionBody extends ScalaPsiElement with ScOptionalBracesOwner with ScExportsHolder {
  def functions: Seq[ScFunction]
}
