package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import api.toplevel.imports.ScImportStmt
import com.intellij.psi.stubs.StubElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.06.2009
 */

trait ScImportStmtStub extends StubElement[ScImportStmt] {
  def getImportText: String
}