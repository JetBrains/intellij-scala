package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging.ScPackagingImpl

/**
  * @author ilyas
  */

class ScPackagingElementType extends ScPackageContainerElementType[ScPackaging]("packaging") {
  override def createElement(node: ASTNode): ScPackaging = new ScPackagingImpl(node)

  override def createPsi(stub: ScPackageContainerStub): ScPackaging = new ScPackagingImpl(stub)
}
