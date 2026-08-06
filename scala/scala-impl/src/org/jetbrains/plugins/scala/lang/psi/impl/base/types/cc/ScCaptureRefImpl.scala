package org.jetbrains.plugins.scala.lang.psi.impl.base.types.cc

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types.cc.ScCaptureRef
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScCaptureRefImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCaptureRef {
  override def toString: String = "CaptureRef"
}
