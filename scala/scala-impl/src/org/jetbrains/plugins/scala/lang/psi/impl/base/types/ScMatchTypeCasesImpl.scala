package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScMatchTypeCase, ScMatchTypeCases}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScMatchTypeCasesImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScMatchTypeCases {
  override def firstCase: ScMatchTypeCase  = findChild[ScMatchTypeCase].get
  override def cases: Seq[ScMatchTypeCase] = findChildren[ScMatchTypeCase]
}
