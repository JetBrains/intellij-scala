package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.{ScDocSyntaxElement, ScDocTag}
import com.intellij.psi.PsiElementVisitor
import lang.psi.api.ScalaElementVisitor

/**
 * User: Dmitry Naidanov
 * Date: 11/14/11
 */

class ScDocSyntaxElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocSyntaxElement{
  override def toString = "DocSyntaxElement " + getFlags

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