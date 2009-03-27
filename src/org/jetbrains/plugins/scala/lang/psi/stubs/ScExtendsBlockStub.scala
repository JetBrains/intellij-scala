package org.jetbrains.plugins.scala.lang.psi.stubs
import api.toplevel.templates.ScExtendsBlock
import com.intellij.psi.stubs.StubElement

/**
 * @author ilyas
 */

trait ScExtendsBlockStub extends StubElement[ScExtendsBlock] {
  def getBaseClasses: Array[String]
}