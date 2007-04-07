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


trait ScalaVariable extends ScalaPsiElement {

  val REFERENCE_SET = TokenSet.create(Array(ScalaElementTypes.VAR_REFERENCE))

  /**
  *   returns list of labels for all variable definitions
  */
  def getVariableNames = childrenOfType[PsiElement](REFERENCE_SET).toList  :::
  {
    val listOfIdentifiers = childSatisfyPredicateForElementType((elem: IElementType) =>
      elem.equals(ScalaElementTypes.IDENTIFIER_LIST)).asInstanceOf[ScalaPsiElement]
    val children = if (listOfIdentifiers != null)
      listOfIdentifiers.childrenOfType[PsiElement](REFERENCE_SET)
    else null
    if (children != null) children.toList
    else Nil: List[PsiElement]
  }

  /**
  *   Returns explicit type of variable, or null, if it is not specified
  */
  def getExplicitType = childSatisfyPredicateForASTNode((node: ASTNode) =>
    node.getPsi.isInstanceOf[ScType])

}



/********************************** IMPLEMENTATIONS  ******************************************/


/**
*   Implements logic, related to definitions of variable with statements like
*   var a = 1
*
*/
case class ScVariableDefinition(node: ASTNode) extends ScalaPsiElementImpl(node)
with ScDefinition with IfElseIndent with ScalaVariable{
  override def toString: String = "variable" + " " + super.toString
  override def getIcon(flags: Int) = Icons.VAR
}


/**
*   Implements logic, related to variable declarations with statements like
*   var a : Int
*
*/
case class ScVariableDeclaration(node: ASTNode) extends ScalaPsiElementImpl(node)
with Declaration with ScalaVariable {
  override def toString: String = "variable" + " " + super.toString
  override def getIcon(flags: Int) = Icons.VAR
}

