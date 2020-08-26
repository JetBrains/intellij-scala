package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSequenceArg
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

trait ScPatternArgumentList extends ScArguments {

  def patterns: collection.Seq[ScPattern]

  def missedLastExpr: Boolean = {
    var child = getLastChild
    while (child != null && child.getNode.getElementType != ScalaTokenTypes.tCOMMA) {
      if (child.isInstanceOf[ScPattern] || child.isInstanceOf[ScSequenceArg])
        return false
      child = child.getPrevSibling
    }
    child != null && child.getNode.getElementType == ScalaTokenTypes.tCOMMA
  }

  override def getArgsCount: Int = patterns.length

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitPatternArgumentList(this)
  }
}

object ScPatternArgumentList {
  def unapplySeq(spl: ScPatternArgumentList): Option[Seq[ScPattern]] = Option(spl.patterns.toSeq)
}