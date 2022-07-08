package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr
package xml

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._

class ScXmlEndTagImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScXmlEndTag{
  override def toString: String = "XmlEndTag"

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitXmlEndTag(this)
}