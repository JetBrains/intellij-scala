package org.jetbrains.plugins.scala.lang.psi.stubs

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

trait ScPackagingStub extends ScStubElement[ScPackaging] {

  def packageName: String

  def parentPackageName: String

  def isExplicit: Boolean
}