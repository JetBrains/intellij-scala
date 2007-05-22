package org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements

/** 
* Created by IntelliJ IDEA.
* User: Ilya.Sergey
* Date: 12.04.2007
* Time: 16:47:58
* To change this template use File | Settings | File Templates.
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


trait ScalaValue extends ScTemplateStatement with ScReferenceIdContainer{

  override def isManyDeclarations = (getChild(ScalaElementTypes.PATTERN2_LIST) != null)

  override def getDeclarations: ScalaPsiElement = getChild(ScalaElementTypes.PATTERN2_LIST).asInstanceOf[ScalaPsiElement]

  override def getIcon(flags: Int) = Icons.VAL

  /**
  *   returns list of labels for all values
  */
  [Nullable]
  override def getNames() = {
    val pattern = childSatisfyPredicateForPsiElement((e: PsiElement) => e.isInstanceOf[ScPattern2])
    (if (pattern != null) {
      val children = pattern.asInstanceOf[ScPattern2].allChildrenOfType[ScReferenceId](ScalaElementTypes.REFERENCE_SET)
      if (children != null) {
        children.toList
      } else {
        Nil: List[ScReferenceId]
      }
    } else {
      Nil: List[ScReferenceId]
    }) ::: childrenOfType[ScReferenceId](ScalaElementTypes.REFERENCE_SET).toList
  }

}

/********************************** IMPLEMENTATIONS  ******************************************/

/**
*  Value pattern definition
*
*/
case class ScPatternDefinition(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDefinition with ScalaValue with IfElseIndent {
  override def toString: String = "pattern" + " " + super.toString

}

/**
*  Value pattern declaration
*
*/
case class ScValueDeclaration(node: ASTNode) extends ScalaPsiElementImpl(node) with ScalaValue with Declaration {
  override def toString: String = "value" + " " + super.toString

  override def getIcon(flags: Int) = Icons.VAL
}

