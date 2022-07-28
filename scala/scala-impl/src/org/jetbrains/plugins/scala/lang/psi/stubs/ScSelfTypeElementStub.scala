package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.NamedStub
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeElementOwnerStub

trait ScSelfTypeElementStub extends NamedStub[ScSelfTypeElement] with ScTypeElementOwnerStub[ScSelfTypeElement] {
  def classNames: Array[String]
}