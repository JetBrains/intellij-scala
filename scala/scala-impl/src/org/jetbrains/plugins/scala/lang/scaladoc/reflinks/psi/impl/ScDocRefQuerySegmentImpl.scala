package org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl.ScDocResolvableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.{ScDocRefQuery, ScDocRefQuerySegment}

class ScDocRefQuerySegmentImpl(node: ASTNode) extends ScDocResolvableCodeReferenceImpl(node) with ScDocRefQuerySegment {
  override def isTopLevelSearch: Boolean = pathQualifier.isEmpty

  override def refName: String = ScDocRefQuery.cleanId(super.refName)

  override def toString: String = {
    val name = refName match {
      case "" => "<error>"
      case s => s"'$s'"
    }
    s"ScDocRefQuerySegment($name)"
  }
}
