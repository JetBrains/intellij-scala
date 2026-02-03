package org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.ScDocRefQuerySegment
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.ScDocRefQuerySegment.IdSelector

class ScDocRefQuerySegmentImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocRefQuerySegment {

  override def toString: String = {
    val text = selector match {
      case None => "<error>"
      case Some(IdSelector(text)) => s"'$text'"
      case Some(other) => other.text
    }
    s"ScDocRefQuerySegment($text)"
  }
}
