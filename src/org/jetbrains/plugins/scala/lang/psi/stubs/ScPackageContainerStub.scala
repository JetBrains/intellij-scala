package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackageContainer

/**
 * @author ilyas
 */

trait ScPackageContainerStub extends StubElement[ScPackageContainer] {
  def prefix: String
  def ownNamePart: String
  def isExplicit: Boolean
}