package org.jetbrains.plugins.scala.lang.psi.impl.statements.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.psi.impl.primitives._
import org.jetbrains.plugins.scala.lang.psi.impl.types._

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.impl.types._

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._


/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 22.02.2008
* Time: 11:19:28
* To change this template use File | Settings | File Templates.
*/

class ScParamImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParam{

  override def toString: String = "Parameter"

 /* def paramType(): ScalaType = {
    val child = getLastChild
    child match {
      case paramType: ScalaType => paramType
      case _ => null
    }
  }*/

 /* override def getExplicitType(id: ScReferenceId) =
    if (getNames.exists((elem: ScReferenceId) => elem.equals(id))){
      val child = childSatisfyPredicateForASTNode((node: ASTNode) => node.getPsi.isInstanceOf[ScalaType])
      if (child != null) {
        child.asInstanceOf[ScalaType].getAbstractType
      } else {
        null
      }
    } else {
      null
    } */


  /**
  *  Returns references to binded values
  */
  /*def getNames = {
    val children = allChildrenOfType[ScReferenceId](ScalaElementTypes.REFERENCE_SET)
    if (children != null) {
      children.toList
    } else {
      Nil: List[ScReferenceId]
    }
  }*/


}