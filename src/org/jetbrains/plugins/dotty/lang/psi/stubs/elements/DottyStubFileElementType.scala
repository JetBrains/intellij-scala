package org.jetbrains.plugins.dotty.lang.psi.stubs.elements

import org.jetbrains.plugins.dotty.lang.psi.stubs.DottyFileStubBuilder
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFileStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType

/**
  * @author adkozlov
  */
class DottyStubFileElementType extends ScStubFileElementType with DottyStubSerializer[ScFileStub] {
  override def getBuilder = new DottyFileStubBuilder
}
