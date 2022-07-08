package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr
package xml

import _root_.org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType}
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing}
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScXmlExprImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScXmlExpr{
  override def toString: String = "XmlExpression"


  protected override def innerType: TypeResult = {
    def getType(s: String): ScType = {
      val typez = ScalaPsiManager.instance(getProject).getCachedClasses(getResolveScope, s).filter(!_.isInstanceOf[ScObject])
      if (typez.length != 0) ScalaType.designator(typez(0))
      else Nothing
    }

    Right(getElements.length match {
      case 0 => Any
      case 1 =>
        getElements.head match {
          case _: ScXmlElement => getType("scala.xml.Elem")
          case _: ScXmlComment => getType("scala.xml.Comment")
          case _: ScXmlCDSect => getType("scala.xml.Text")
          case _: ScXmlPI => getType("scala.xml.ProcInstr")
        }
      case _ =>
        getType("scala.xml.NodeBuffer")
    })
  }
}