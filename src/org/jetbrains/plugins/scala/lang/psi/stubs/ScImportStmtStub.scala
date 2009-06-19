package org.jetbrains.plugins.scala.lang.psi.stubs


import api.toplevel.imports.ScImportStmt
import com.intellij.psi.stubs.StubElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.06.2009
 */

trait ScImportStmtStub extends StubElement[ScImportStmt] {
  def getImportText: String
}