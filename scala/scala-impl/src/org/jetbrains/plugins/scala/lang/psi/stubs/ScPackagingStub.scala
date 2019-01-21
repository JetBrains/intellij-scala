package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement

trait ScPackagingStub extends StubElement[api.toplevel.ScPackaging] {

  def packageName: String

  def parentPackageName: String

  def isExplicit: Boolean
}