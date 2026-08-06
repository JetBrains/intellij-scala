package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

class ScTopLevelImplicitClassByPackageIndex extends ScStringStubIndexExtension[ScClass] {
  override def getKey: StubIndexKey[String, ScClass] =
    ScalaIndexKeys.TOP_LEVEL_IMPLICIT_CLASS_BY_PKG_KEY
}
