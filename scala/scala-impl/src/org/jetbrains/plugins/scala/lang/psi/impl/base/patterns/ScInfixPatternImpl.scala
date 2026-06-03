package org.jetbrains.plugins.scala.lang.psi.impl.base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

final class ScInfixPatternImpl(node: ASTNode) extends ScExtractorPatternImpl(node) with ScInfixPattern {
  override def toString: String = "InfixPattern"

  override def subpatterns: Seq[ScPattern] = {
    val subpatterns = this.findChildren[ScPattern]

    subpatterns match {
      case Seq(left, right: ScTuplePattern) if !this.isInScala3File =>
        // In Scala 2, if an infix pattern has a tuple pattern as the right argument that is interpreted as arguments
        left +: right.subpatterns
      case _ =>
        subpatterns
    }
  }
}