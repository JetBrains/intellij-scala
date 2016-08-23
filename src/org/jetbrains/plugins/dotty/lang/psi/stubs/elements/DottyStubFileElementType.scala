package org.jetbrains.plugins.dotty.lang.psi.stubs.elements

import com.intellij.psi.StubBuilder
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.dotty.lang.DottyTokenSets
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFileStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScFileStub, ScalaFileStubBuilder}

/**
  * @author adkozlov
  */
class DottyStubFileElementType
  extends ScStubFileElementType with DottyDefaultStubSerializer[ScFileStub] {

  override def getBuilder: StubBuilder = new ScalaFileStubBuilder {
    override protected def createStubForFile(file: ScalaFile) =
      new ScFileStubImpl(file) {
        override protected def tokenSets = DottyTokenSets
      }
  }

  override protected def deserializedStub(isScript: Boolean, isCompiled: Boolean,
                                          packageNameRef: StringRef, sourceNameRef: StringRef) =
    new DeserializedStubImpl(isScript, isCompiled,
      packageNameRef, sourceNameRef) {
      override protected def tokenSets = DottyTokenSets
    }
}
