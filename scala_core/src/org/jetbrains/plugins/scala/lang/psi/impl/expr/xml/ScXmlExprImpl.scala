package org.jetbrains.plugins.scala.lang.psi.impl.expr.xml

import _root_.org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScDesignatorType, ScType}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType

import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._

/**
* @author Alexander Podkhalyuzin
* Date: 21.04.2008
*/

class ScXmlExprImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScXmlExpr{
  override def toString: String = "XmlExpression"


  override def getType(): ScType = {
    def getType(s: String): ScType = {
      val nodeType = JavaPsiFacade.getInstance(getProject).findClass(s)
      if (nodeType != null) new ScDesignatorType(nodeType) else types.Nothing
    }
    getElements.length match {
      case 0 => types.Nothing
      case 1 => {
        getElements(0) match {
          case _: ScXmlElement => getType("scala.xml.Elem")
          case _: ScXmlComment => getType("scala.xml.Comment")
          case _: ScXmlCDSect => getType("scala.xml.Text")
          case _: ScXmlPI => getType("scala.xml.ProcInstr")
        }
      }
      case _ => {
        getType("scala.xml.NodeBuffer")
      }
    }
  }
}