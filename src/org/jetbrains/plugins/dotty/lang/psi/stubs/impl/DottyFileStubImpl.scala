package org.jetbrains.plugins.dotty.lang.psi.stubs.impl

import com.intellij.util.io.StringRef
import org.jetbrains.plugins.dotty.lang.DottyTokenSets
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFileStubImpl

/**
  * @author adkozlov
  */
class DottyFileStubImpl(file: ScalaFile, packageName: StringRef, fileName: StringRef, isCompiled: Boolean, isScript: Boolean)
  extends ScFileStubImpl(file, packageName, fileName, isCompiled, isScript) {
  override protected lazy val tokenSets = DottyTokenSets
}
