package org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.ScDocRefStrictMemberIdQuery

class ScDocRefStrictMemberIdQueryImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocRefStrictMemberIdQuery {
  override def toString: String = s"ScDocRefStrictMemberIdQuery(${memberId.getOrElse("<error>")})"
}
