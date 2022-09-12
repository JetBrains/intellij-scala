package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

trait ScPackagingStub extends StubElement[ScPackaging] {

  def packageName: String

  def parentPackageName: String

  def isExplicit: Boolean
}