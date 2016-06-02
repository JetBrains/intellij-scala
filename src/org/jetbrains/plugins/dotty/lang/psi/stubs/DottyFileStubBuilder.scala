package org.jetbrains.plugins.dotty.lang.psi.stubs

import com.intellij.util.io.StringRef
import org.jetbrains.plugins.dotty.lang.psi.stubs.impl.DottyFileStubImpl
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.stubs.ScalaFileStubBuilder

/**
  * @author adkozlov
  */
class DottyFileStubBuilder extends ScalaFileStubBuilder {
  override def fileStub(file: ScalaFile, packageName: StringRef, fileName: StringRef, compiled: Boolean, script: Boolean) =
    new DottyFileStubImpl(file, packageName, fileName, compiled, script)
}
