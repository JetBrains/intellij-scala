package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging.ScPackagingImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging

/**
 * @author ilyas
 */

class ScPackagingElementType extends ScPackageContainerElementType[ScPackaging]("packaging") {

  def createElement(node: ASTNode): PsiElement = new ScPackagingImpl(node)

  def createPsi(stub: ScPackageContainerStub): ScPackaging = new ScPackagingImpl(stub)
}
