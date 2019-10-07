package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package imports

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportStmtStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScImportStmtElementType

final class ScExportStmtImpl(stub: ScImportStmtStub,
                             nodeType: ScImportStmtElementType,
                             node: ASTNode,
                             override val toString: String)
  extends ScImportStmtImpl(stub, nodeType, node, toString)
    with ScImportStmt
