package org.jetbrains.plugins.dotty.lang.psi.stubs.elements

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.DefaultStubSerializer

/**
  * @author adkozlov
  */
trait DottyDefaultStubSerializer[S <: StubElement[_]] extends DefaultStubSerializer[S] {
  override def getExternalId = s"dotty.$debugName"
}
