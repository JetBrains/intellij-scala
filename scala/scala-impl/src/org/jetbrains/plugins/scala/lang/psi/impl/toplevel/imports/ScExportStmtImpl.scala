package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package imports

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportStmtStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStatementElementType

final class ScExportStmtImpl private[psi](stub: ScImportStmtStub,
                                          nodeType: ScStatementElementType,
                                          node: ASTNode)
  extends ScImportStmtImpl(stub, nodeType, node) {

  override def toString: String = "ScExportStatement"
}
