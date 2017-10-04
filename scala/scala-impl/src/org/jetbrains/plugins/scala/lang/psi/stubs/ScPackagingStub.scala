package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

/**
  * @author ilyas
  */
trait ScPackagingStub extends StubElement[ScPackaging] {
  def parentPackageName: String

  def packageName: String

  def isExplicit: Boolean
}