package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScPatternListImpl

final class ScPatternListElementType extends ScStubElementType[ScPatternList]("pattern list") {
  override def createElement(node: ASTNode): ScPatternList = new ScPatternListImpl(node)
}
