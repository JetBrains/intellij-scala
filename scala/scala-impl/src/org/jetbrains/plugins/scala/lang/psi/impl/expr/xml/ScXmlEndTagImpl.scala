package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr
package xml

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._

/**
* @author Alexander Podkhalyuzin
* Date: 21.04.2008
*/

class ScXmlEndTagImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScXmlEndTag{
  override def toString: String = "XmlEndTag"

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case scVisitor: ScalaElementVisitor => scVisitor.visitXmlEndTag(this)
      case _ => super.accept(visitor)
    }
  }
}