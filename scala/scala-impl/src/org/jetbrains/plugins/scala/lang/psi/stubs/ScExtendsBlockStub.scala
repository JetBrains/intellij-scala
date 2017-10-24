package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock

/**
  * @author ilyas
  */
trait ScExtendsBlockStub extends StubElement[ScExtendsBlock] {
  def baseClasses: Array[String]
}