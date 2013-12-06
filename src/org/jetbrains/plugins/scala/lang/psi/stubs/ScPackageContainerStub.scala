package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import api.toplevel.packaging.ScPackageContainer
import com.intellij.psi.stubs.StubElement

/**
 * @author ilyas
 */

trait ScPackageContainerStub extends StubElement[ScPackageContainer] {
  def prefix: String
  def ownNamePart: String
  def isExplicit: Boolean
}