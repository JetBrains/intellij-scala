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

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.DclDef

  class TemplateStatement (node : ASTNode) extends ScalaPsiElementImpl (node) {
    override def copy() : PsiElement = {
      DclDef.createTemplateStatementFromText(this.getText, this.getManager).getPsi()
    }

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

    private def isManyDeclarations : Boolean = (getChild(ScalaElementTypes.IDENTIFIER_LIST) != null)

    private def getDeclarations : ScIdentifierList = getChild(ScalaElementTypes.IDENTIFIER_LIST).asInstanceOf[ScIdentifierList]

    [NotNull]
    protected def names : Iterable[PsiElement] = {
      if (isManyDeclarations) return getDeclarations.childrenOfType[PsiElement](TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER)))
      if (isConstructor) return childrenOfType[PsiElement](TokenSet.create(Array(ScalaTokenTypes.kTHIS)))

      childrenOfType[PsiElement](TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER)))
    }

//    [NotNull]
//    protected def names : Iterable[ScalaPsiElementImpl] = {
//      if (isManyDeclarations) return getDeclarations.childrenOfType[ScalaPsiElementImpl](TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER)))
//      if (isConstructor) return getChild(ScalaTokenTypes.kTHIS)
//
//      getChild(ScalaTokenTypes.tIDENTIFIER)
//    }

  }

  /***************** definition ***********************/

  class Definition (node : ASTNode) extends TemplateStatement (node) {
    override def toString: String = "definition"
  }

  case class ScPatternDefinition (node : ASTNode) extends Definition (node) with IfElseIndent{
    override def toString: String = "pattern" + " " + super.toString

    private def isManyDeclarations = (getChild(ScalaElementTypes.PATTERN2_LIST) != null)

    private def getDeclarations : ScPattern2List = getChild(ScalaElementTypes.PATTERN2_LIST).asInstanceOf[ScPattern2List]

    [NotNull]
    override def names : Iterable[PsiElement] = {
      if (isManyDeclarations) return getDeclarations.childrenOfType[PsiElement](TokenSet.create(Array(ScalaElementTypes.PATTERN2)))

      childrenOfType[PsiElement](TokenSet.create(Array(ScalaElementTypes.PATTERN2)))
    }
//     def isPattern2 = (e : PsiElement) => e.isInstanceOf[ScPattern2]
//
//     [Nullable]
//     override def stmtNamesNodes: Iterable[ScalaPsiElement] = {
//       if (isManyDeclarations) return getDeclarations.childrenSatisfyPredicateForPsiElement[ScalaPsiElementImpl](isPattern2)
//
//       childrenSatisfyPredicateForPsiElement[ScalaPsiElementImpl](isPattern2)
//     }

//     override def nameNode = childSatisfyPredicateForPsiElement(isPattern2)
  }

  case class ScVariableDefinition (node : ASTNode) extends Definition (node) with IfElseIndent{
    override def toString: String = "variable" + " " + super.toString
  }

  case class ScFunctionDefinition (node : ASTNode) extends Definition (node) with IfElseIndent{
    override def toString: String = "function" + " " + super.toString

//    override def names : Iterable[ScalaPsiElementImpl] = {
//
//    }
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


  case class ScTypeDefinition (node : ASTNode) extends Definition (node) {
    override def toString: String = "type" + " " + super.toString
  }

  /***************** declaration ***********************/

  class Declaration (node : ASTNode) extends TemplateStatement (node) {
    override def toString: String = "declaration"
  }

  case class ScValueDeclaration (node : ASTNode) extends Declaration (node) {
    override def toString: String = "value" + " " + super.toString
  }

  case class ScVariableDeclaration (node : ASTNode) extends Declaration (node) {
    override def toString: String = "variable" + " " + super.toString
  }

  case class ScFunctionDeclaration (node : ASTNode) extends Declaration (node) {
    override def toString: String = "function" + " " + super.toString
  }

  case class ScTypeDeclaration (node : ASTNode) extends Declaration (node) {
    override def toString: String = "type" + " " + super.toString
  }

  /************** function signature *********************/

  class ScFunctionSignature (node : ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "function signature"
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