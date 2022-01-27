package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector

class ScAliasedImportKey extends ScStringStubIndexExtension[ScImportSelector] {
  override def getKey: StubIndexKey[String, ScImportSelector] =
    ScalaIndexKeys.ALIASED_IMPORT_KEY
}
