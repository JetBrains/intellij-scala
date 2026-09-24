package org.jetbrains.plugins.scala.lang.psi.stubs

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension

trait ScExtensionStub
  extends ScTopLevelElementStub[ScExtension] with ScStubElement[ScExtension] {

  def extensionTargetClass: Option[String]
}
