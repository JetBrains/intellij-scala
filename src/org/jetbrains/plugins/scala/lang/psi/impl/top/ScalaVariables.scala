package org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements

/**
 * @author Ilya Sergey
 */


import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.psi.impl.primitives._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.psi.impl.top.params._
import org.jetbrains.plugins.scala.lang.psi.impl.top._

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.DclDef
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.impl.types._


trait ScalaVariable extends ScalaPsiElement with ScReferenceIdContainer {

  /**
  *   returns list of labels for all variable definitions
  */
  def getNames = childrenOfType[ScReferenceId](ScalaElementTypes.REFERENCE_SET).toList :::
  {
    val listOfIdentifiers = childSatisfyPredicateForElementType((elem: IElementType) =>
      elem.equals(ScalaElementTypes.IDENTIFIER_LIST)).asInstanceOf[ScalaPsiElement]
    val children = if (listOfIdentifiers != null)
      listOfIdentifiers.childrenOfType[ScReferenceId](ScalaElementTypes.REFERENCE_SET)
    else null
    if (children != null) children.toList
    else Nil: List[ScReferenceId]
  }

  /**
  *   Returns explicit type of variable, or null, if it is not specified
  */
  override def getExplicitType(id: ScReferenceId) =
    if (getNames.exists((elem: ScReferenceId) => elem.equals(id))){
      val child = childSatisfyPredicateForASTNode((node: ASTNode) => node.getPsi.isInstanceOf[ScalaType])
      if (child != null) {
        child.asInstanceOf[ScalaType].getAbstractType
      } else {
        null
      }
    } else {
      null
    }

  /**
  *   Returns infered type of variable, or null in case of any problems with inference
  */
  override def getInferedType(id: ScReferenceId) = {
    val child = childSatisfyPredicateForPsiElement((el: PsiElement) => el.isInstanceOf[ScalaExpression])
    if (child != null) {
      child.asInstanceOf[ScalaExpression].getAbstractType
    } else {
      null
    }
  }

}



/********************************** IMPLEMENTATIONS  ******************************************/


/**
*   Implements logic, related to definitions of variable with statements like
*   var a = 1
*
*/
case class ScVariableDefinition(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDefinition with IfElseIndent with ScalaVariable{
  override def toString: String = "variable" + " " + super.toString
  override def getIcon(flags: Int) = Icons.VAR
}


/**
*   Implements logic, related to variable declarations with statements like
*   var a : Int
*
*/
case class ScVariableDeclaration(node: ASTNode) extends ScalaPsiElementImpl(node) with Declaration with ScalaVariable {
  override def toString: String = "variable" + " " + super.toString
  override def getIcon(flags: Int) = Icons.VAR
}

