package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

final class ScAnnotatedMainFunctionIndex extends ScStringStubIndexExtension[ScFunction] {

  override def getKey: StubIndexKey[String, ScFunction] =
    ScalaIndexKeys.ANNOTATED_MAIN_FUNCTION_BY_PKG_KEY
}
