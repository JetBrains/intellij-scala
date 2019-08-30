package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtendsBlockStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScExtendsBlockElementType

final class ScDerivesBlockImpl private[psi](stub: ScExtendsBlockStub,
                                            nodeType: ScExtendsBlockElementType,
                                            node: ASTNode)
  extends ScExtendsBlockImpl(stub, nodeType, node) with ScExtendsBlock {

  override def toString: String = "ScDerivesBlock"
}
