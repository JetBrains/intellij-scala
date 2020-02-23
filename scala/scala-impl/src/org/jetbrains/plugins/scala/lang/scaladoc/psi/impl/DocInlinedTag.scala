package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl


import _root_.org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.javadoc.PsiDocTagValue
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocInlinedTag

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocInlinedTagImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocInlinedTag{
  override def toString: String = "DocInlinedTag"

  override def getValueElement: PsiDocTagValue = findChildByClass(classOf[PsiDocTagValue])

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitInlinedTag(this)
  }
}