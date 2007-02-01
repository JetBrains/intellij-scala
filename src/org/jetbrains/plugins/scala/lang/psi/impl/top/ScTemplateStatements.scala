package org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements {


/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2006
 * Time: 16:32:36
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

  trait TemplateStatement extends ScalaPsiElement {
    override def copy() : PsiElement = DclDef.createTemplateStatementFromText(this.getText, this.getManager).getPsi()

    private def isDefinitionPredicate = (elementType : IElementType) => (elementType == ScalaTokenTypes.kTHIS)

    private def isThis = (elementType : IElementType) => (elementType == ScalaTokenTypes.kTHIS)

    def isConstructor = (childSatisfyPredicateForElementType(isThis) != null)

    [NotNull]
    def asDisjunctNodes : Iterable[TemplateStatement] = {
      if (!isManyDeclarations) return Array(this.copy.asInstanceOf[TemplateStatement])

      val theNames = names
      for (val name <- theNames) yield {
        val thisCopy : TemplateStatement = this.copy.asInstanceOf[TemplateStatement]

        thisCopy.getNode.replaceChild(thisCopy.getDeclarations.getNode, name.copy.getNode)
        thisCopy
      }
    }


    [Nullable]
    def getType : ScType = {
      def isType = (e : IElementType) => ScalaElementTypes.TYPE_BIT_SET.contains(e)

//      val theNames = names
//      val listNames = theNames.toList
//      if (theNames != null && listNames.last != null) {

//        childSatisfyPredicateForElementType(isType, listNames.last.getNextSibling).asInstanceOf[ScType]
//      } else null
      val colon = getChild(ScalaTokenTypes.tCOLON)
      if (colon == null) return null

      childSatisfyPredicateForElementType(isType, colon.getNextSibling).asInstanceOf[ScType]
    }

    def getShortName : String = {
      val theName = names.elements.next
      if (theName != null) theName.getText
      else "no name"
    }

    override def getTextOffset = {
      val theName = names.elements.next;
      if (theName != null) theName.getTextRange.getStartOffset
      else this.getTextRange.getStartOffset
    }

    protected def isManyDeclarations : Boolean = (getChild(ScalaElementTypes.IDENTIFIER_LIST) != null)
    protected def getDeclarations : ScalaPsiElement = getChild(ScalaElementTypes.IDENTIFIER_LIST).asInstanceOf[ScalaPsiElement]

    [NotNull]
    protected def names : Iterable[PsiElement] = {
      if (isManyDeclarations) return getDeclarations.childrenOfType[PsiElement](TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER)))
      if (isConstructor) return childrenOfType[PsiElement](TokenSet.create(Array(ScalaTokenTypes.kTHIS)))

      childrenOfType[PsiElement](TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER)))
    }
  }

  trait Function extends TemplateStatement {
    def getFunSig : ScFunctionSignature = getFirstChild.asInstanceOf[ScFunctionSignature]
  }

  trait Type extends TemplateStatement {
//    override def getType = null

    [Nullable]
    def getLowerBoundType = {
      val lowerBound = getChild(ScalaElementTypes.LOWER_BOUND_TYPE)
      if (lowerBound != null) lowerBound.asInstanceOf[ScalaPsiElement].getLastChild
      else null
    }

    [Nullable]
    def getUpperBoundType = {
      val upperBound = getChild(ScalaElementTypes.UPPER_BOUND_TYPE)
      if (upperBound != null) upperBound.asInstanceOf[ScalaPsiElement].getLastChild
      else null
    }
  }

  /***************** definition ***********************/

  trait  Definition extends TemplateStatement {
    override def toString: String = "definition"

//    def getExpr =
  }

  case class ScPatternDefinition (node : ASTNode) extends ScalaPsiElementImpl(node) with Definition with IfElseIndent{
    override def toString: String = "pattern" + " " + super.toString

    override def isManyDeclarations = (getChild(ScalaElementTypes.PATTERN2_LIST) != null)
    override def getDeclarations : ScalaPsiElement = getChild(ScalaElementTypes.PATTERN2_LIST).asInstanceOf[ScalaPsiElement]

    [NotNull]
    override def names : Iterable[PsiElement] = {
      if (isManyDeclarations) return getDeclarations.childrenOfType[PsiElement](ScalaElementTypes.PATTERN2_BIT_SET)

      childrenOfType[PsiElement](ScalaElementTypes.PATTERN2_BIT_SET)
    }
  }

  case class ScVariableDefinition (node : ASTNode) extends ScalaPsiElementImpl(node) with Definition with IfElseIndent{
    override def toString: String = "variable" + " " + super.toString
  }

  case class ScFunctionDefinition (node : ASTNode) extends ScalaPsiElementImpl(node) with Function with Definition with IfElseIndent {
    override def toString: String = "function" + " " + super.toString
  }

  /************** supplementary constructor ***************/

  case class ScSelfInvocation (node : ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "self invocation"
  }

  case class ScConstrExpr (node : ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "constructor expression"
  }

  case class ScSupplementaryConstructor (node : ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "supplementary constructor"
  }

  case class ScTypeDefinition (node : ASTNode) extends ScalaPsiElementImpl(node) with Definition with Type {
    override def toString: String = "type" + " " + super.toString
  }

  /***************** declaration ***********************/

  trait Declaration extends TemplateStatement {
    override def toString: String = "declaration"
  }

  case class ScValueDeclaration (node : ASTNode) extends ScalaPsiElementImpl(node) with Declaration {
    override def toString: String = "value" + " " + super.toString
  }

  case class ScVariableDeclaration (node : ASTNode) extends ScalaPsiElementImpl(node) with Declaration  {
    override def toString: String = "variable" + " " + super.toString
  }

  case class ScFunctionDeclaration (node : ASTNode) extends ScalaPsiElementImpl(node) with Declaration with Function {
    override def toString: String = "function" + " " + super.toString
  }

  case class ScTypeDeclaration (node : ASTNode) extends ScalaPsiElementImpl(node) with Declaration with Type {
    override def toString: String = "type" + " " + super.toString
  }

  /************** function signature *********************/

  class ScFunctionSignature (node : ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "function signature"

    private def isTypeParamClause = (e : PsiElement) => e.isInstanceOf[ScTypeParamClause]
    def getTypeParamClause : ScTypeParamClause = childSatisfyPredicateForPsiElement(isTypeParamClause).asInstanceOf[ScTypeParamClause]

    private def isParamClauses = (e : PsiElement) => e.isInstanceOf[ScParamClauses]
    def getParamClauses : ScParamClauses = childSatisfyPredicateForPsiElement(isParamClauses).asInstanceOf[ScParamClauses]
  }

  /****************** variable ************************/

  class ScIdentifierList (node : ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "list of identifiers"
  }

  /****************** pattern2List  ************************/

  class ScPattern2List (node : ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "list of pattern2"
  }
}