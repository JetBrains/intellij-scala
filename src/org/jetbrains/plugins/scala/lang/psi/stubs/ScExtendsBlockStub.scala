package org.jetbrains.plugins.scala
package lang
package psi
package stubs
import api.toplevel.templates.ScExtendsBlock
import com.intellij.psi.stubs.StubElement

/**
 * @author ilyas
 */

trait ScExtendsBlockStub extends StubElement[ScExtendsBlock] {
  def getBaseClasses: Array[String]
}