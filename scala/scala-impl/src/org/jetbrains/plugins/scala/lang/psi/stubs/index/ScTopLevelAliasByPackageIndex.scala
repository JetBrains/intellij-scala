package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

class ScTopLevelAliasByPackageIndex extends ScStringStubIndexExtension[ScTypeAlias] {
  override def getKey: StubIndexKey[String, ScTypeAlias] =
    ScalaIndexKeys.TOP_LEVEL_TYPE_ALIAS_BY_PKG_KEY
}


