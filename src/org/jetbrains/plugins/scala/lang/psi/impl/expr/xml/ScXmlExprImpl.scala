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
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}

/**
* @author Alexander Podkhalyuzin
*/

class ScXmlExprImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScXmlExpr{
  override def toString: String = "XmlExpression"


  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    def getType(s: String): ScType = {
      val typez = ScalaPsiManager.instance(getProject).getCachedClasses(getResolveScope, s).filter(!_.isInstanceOf[ScObject])
      if (typez.length != 0) ScalaType.designator(typez(0))
      else types.Nothing
    }
    Success(getElements.length match {
      case 0 => types.Any
      case 1 =>
        getElements.head match {
          case _: ScXmlElement => getType("scala.xml.Elem")
          case _: ScXmlComment => getType("scala.xml.Comment")
          case _: ScXmlCDSect => getType("scala.xml.Text")
          case _: ScXmlPI => getType("scala.xml.ProcInstr")
        }
      case _ =>
        getType("scala.xml.NodeBuffer")
    }, Some(this))
  }
}