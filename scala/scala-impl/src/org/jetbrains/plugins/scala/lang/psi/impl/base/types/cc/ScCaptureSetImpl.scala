package org.jetbrains.plugins.scala.lang.psi.impl.base.types.cc

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types.cc.ScCaptureSet
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScCaptureSetImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCaptureSet {
  override def toString: String = "CaptureSet"
}
