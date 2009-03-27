package org.jetbrains.plugins.scala.lang.psi.stubs

import api.toplevel.packaging.ScPackageContainer
import com.intellij.psi.stubs.{StubElement, NamedStub}
import com.intellij.util.io.StringRef

/**
 * @author ilyas
 */

trait ScPackageContainerStub extends StubElement[ScPackageContainer] {
  def prefix : String
  def ownNamePart : String
}