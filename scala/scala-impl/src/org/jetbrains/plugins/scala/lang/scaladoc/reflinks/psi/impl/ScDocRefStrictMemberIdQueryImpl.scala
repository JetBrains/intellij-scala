package org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl.ScDocResolvableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.ScDocRefStrictMemberIdQuery

class ScDocRefStrictMemberIdQueryImpl(node: ASTNode) extends ScDocResolvableCodeReferenceImpl(node) with ScDocRefStrictMemberIdQuery {
  override def localReferenceSearch: Boolean = true

  override def refName: String = super.memberId.getOrElse("")
  override def toString: String = s"ScDocRefStrictMemberIdQuery(${memberId.getOrElse("<error>")})"
}
