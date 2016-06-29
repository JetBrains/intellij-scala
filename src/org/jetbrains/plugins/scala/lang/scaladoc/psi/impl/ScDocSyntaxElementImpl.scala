package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocSyntaxElement

/**
 * User: Dmitry Naidanov
 * Date: 11/14/11
 */

class ScDocSyntaxElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocSyntaxElement{
  override def toString: String = "DocSyntaxElement " + getFlags

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => accept(s)
      case _ => super.accept(visitor)
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitWikiSyntax(this)
  }
}