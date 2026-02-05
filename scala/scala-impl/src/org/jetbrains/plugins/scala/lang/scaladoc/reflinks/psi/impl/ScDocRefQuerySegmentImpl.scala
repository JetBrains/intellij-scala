package org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl.ScDocResolvableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.ScDocRefQuerySegment
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.ScDocRefQuerySegment.IdSelector

class ScDocRefQuerySegmentImpl(node: ASTNode) extends ScDocResolvableCodeReferenceImpl(node) with ScDocRefQuerySegment {
  override def refName: String = selector match {
    case Some(IdSelector(text)) => text
    case _ => ""
  }

  override def toString: String = {
    val text = selector match {
      case None => "<error>"
      case Some(IdSelector(text)) => s"'$text'"
      case Some(other) => other.text
    }
    s"ScDocRefQuerySegment($text)"
  }
}
