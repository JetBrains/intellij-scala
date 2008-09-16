package org.jetbrains.plugins.scala.lang.psi.stubs

import api.toplevel.packaging.ScPackageContainer
import com.intellij.psi.stubs.{StubElement, NamedStub}
import com.intellij.util.io.StringRef
import elements.wrappers.StubElementWrapper

/**
 * @author ilyas
 */

trait ScPackageContainerStub extends StubElement[ScPackageContainer] with StubElementWrapper[ScPackageContainer] {

  implicit def refToStr(ref: StringRef): String = StringRef.toString(ref)

  def fqn: String

}