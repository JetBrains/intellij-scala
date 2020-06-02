package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.javadoc.PsiDocTagValue
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocInlinedTag

class ScDocInlinedTagImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocInlinedTag{
  override def toString: String = "DocInlinedTag"

  override def getValueElement: PsiDocTagValue =
    findChildByClass(classOf[PsiDocTagValue])

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitInlinedTag(this)
}