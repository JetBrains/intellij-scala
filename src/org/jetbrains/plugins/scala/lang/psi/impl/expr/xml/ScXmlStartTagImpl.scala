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

class ScXmlStartTagImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScXmlStartTag{
  override def toString: String = "XmlStartTag"

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case scVisitor: ScalaElementVisitor => scVisitor.visitXmlStartTag(this)
      case _ => super.accept(visitor)
    }
  }
}