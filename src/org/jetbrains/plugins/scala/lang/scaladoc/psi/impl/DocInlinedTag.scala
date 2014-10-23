package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl


import _root_.org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.javadoc.PsiDocTagValue
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocInlinedTag

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocInlinedTagImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocInlinedTag{
  override def toString: String = "DocInlinedTag"

  def getValueElement: PsiDocTagValue = findChildByClass(classOf[PsiDocTagValue])

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => accept(s)
      case _ => super.accept(visitor)
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitInlinedTag(this)
  }
}