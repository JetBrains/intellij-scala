package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScIdList
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScIdListImpl

final class ScIdListElementType extends ScStubElementType[ScIdList]("id list") {
  override def createElement(node: ASTNode): ScIdList = new ScIdListImpl(node)
}
