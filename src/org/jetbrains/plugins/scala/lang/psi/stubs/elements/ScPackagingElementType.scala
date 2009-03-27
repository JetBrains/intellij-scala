package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging.ScPackagingImpl
import api.toplevel.packaging.ScPackaging
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * @author ilyas
 */

class ScPackagingElementType extends ScPackageContainerElementType[ScPackaging]("packaging") {

  def createElement(node: ASTNode): PsiElement = new ScPackagingImpl(node)

  def createPsi(stub: ScPackageContainerStub): ScPackaging = new ScPackagingImpl(stub)
}
