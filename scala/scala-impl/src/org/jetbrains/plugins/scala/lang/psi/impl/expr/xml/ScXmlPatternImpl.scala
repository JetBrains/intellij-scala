package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr
package xml

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScPattern, ScPatterns}
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result._

import scala.collection.mutable.ArrayBuffer

/**
* @author Alexander Podkhalyuzin
* Date: 21.04.2008
*/

class ScXmlPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScXmlPattern {
  override def accept(visitor: PsiElementVisitor) {
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

  override def `type`(): TypeResult = {
    val clazz = ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "scala.xml.Node").orNull
    if (clazz == null) return Failure("not found scala.xml.Node")
    Right(ScDesignatorType(clazz))
  }
}