package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

class ScTopLevelFunctionByPackageIndex extends ScStringStubIndexExtension[ScFunction] {
  override def getKey: StubIndexKey[String, ScFunction] =
    ScalaIndexKeys.TOP_LEVEL_FUNCTION_BY_PKG_KEY
}
