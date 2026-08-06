package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt

class ScTopLevelExportByPackageIndex extends ScStringStubIndexExtension[ScExportStmt] {
  override def getKey: StubIndexKey[String, ScExportStmt] = ScalaIndexKeys.TOP_LEVEL_EXPORT_BY_PKG_KEY
}
