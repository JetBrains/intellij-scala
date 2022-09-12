package org.jetbrains.plugins.scala.lang.psi.impl.expr
package xml

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScXmlElementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScXmlElement{
  override def toString: String = "XmlElement"
}