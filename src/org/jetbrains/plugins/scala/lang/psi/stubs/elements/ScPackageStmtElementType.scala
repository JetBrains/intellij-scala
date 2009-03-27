package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging.ScPackageStatementImpl
import api.toplevel.packaging.ScPackageStatement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * @author ilyas
 */

class ScPackageStmtElementType extends ScPackageContainerElementType[ScPackageStatement]("package statement") {

  def createElement(node: ASTNode): PsiElement = new ScPackageStatementImpl(node)

  def createPsi(stub: ScPackageContainerStub) = new ScPackageStatementImpl(stub)
}
