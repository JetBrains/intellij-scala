package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr
package xml

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.ScalaElementVisitor
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import collection.mutable.ArrayBuffer
import api.base.ScPatternList
import api.base.patterns.{ScPatterns, ScPattern}

/**
* @author Alexander Podkhalyuzin
* Date: 21.04.2008
*/

class ScXmlPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScXmlPattern {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def subpatterns: Seq[ScPattern] = {
    val pattBuff: ArrayBuffer[ScPattern] = new ArrayBuffer[ScPattern]
    pattBuff ++= super.subpatterns
    val args = findChildrenByClassScala(classOf[ScPatterns])
    for (arg <- args) {
      pattBuff ++= arg.patterns
    }
    pattBuff.toSeq
  }

  override def toString: String = "XmlPattern"
}