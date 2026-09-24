package org.jetbrains.plugins.scala.lang.psi.stubs

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock

trait ScExtendsBlockStub extends ScStubElement[ScExtendsBlock] {
  def baseClasses: Seq[String]
}