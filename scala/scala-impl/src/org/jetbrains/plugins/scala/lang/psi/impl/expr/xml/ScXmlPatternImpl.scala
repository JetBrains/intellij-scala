package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr
package xml

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScPattern, ScPatterns}
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScPatternImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScXmlPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPatternImpl with ScXmlPattern {
  override def isIrrefutableFor(t: Option[ScType]): Boolean = false

  override def subpatterns: Seq[ScPattern] =
    super.subpatterns ++ findChildren[ScPatterns].iterator.flatMap(_.patterns)

  override def toString: String = "XmlPattern"

  override def `type`(): TypeResult = {
    val clazz = ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "scala.xml.Node").orNull
    if (clazz == null) return Failure(ScalaBundle.message("not.found.scala.xml.node"))
    Right(ScDesignatorType(clazz))
  }
}