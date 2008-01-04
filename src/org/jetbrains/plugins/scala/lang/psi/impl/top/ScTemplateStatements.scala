package org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements


/**
* User: Dmitry.Krasilschikov
* Date: 13.11.2006
* Time: 16:32:36
*/

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.containers._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.psi.impl.primitives._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.psi.impl.top.params._
import org.jetbrains.plugins.scala.lang.typechecker.types._

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.DclDef
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons

trait ScTemplateStatement extends ScalaPsiElement {
  override def copy(): PsiElement = ScalaPsiElementFactory.createTemplateStatementFromText(this.getText, this.getManager).getPsi

  private def isDefinitionPredicate = (elementType: IElementType) => (elementType == ScalaTokenTypes.kTHIS)

  private def isThis = (elementType: IElementType) => (ScalaTokenTypes.kTHIS.equals(elementType))

  [Nullable]
  def getType: ScalaPsiElement = {
    def isType = (e: IElementType) => ScalaElementTypes.TYPE_BIT_SET.contains(e)
    val colon = getChild(ScalaTokenTypes.tCOLON)
    if (colon == null) return null

    childSatisfyPredicateForElementType(isType, colon.getNextSibling).asInstanceOf[ScalaPsiElement]
  }

  def getShortName: String = {
    val theName = names.elements.next
    if (theName != null) theName.getText
    else "no name"
  }

  override def getTextOffset = {
    val theName = names.elements.next;
    if (theName != null) theName.getTextRange.getStartOffset
    else this.getTextRange.getStartOffset
  }

  def isManyDeclarations: Boolean = (getChild(ScalaElementTypes.IDENTIFIER_LIST) != null)
  def getDeclarations: ScalaPsiElement = getChild(ScalaElementTypes.IDENTIFIER_LIST).asInstanceOf[ScalaPsiElement]

  [NotNull]
  def names: Iterable[PsiElement] = {
    if (isManyDeclarations) return getDeclarations.childrenOfType[PsiElement](TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER)))

    childrenOfType[PsiElement](TokenSet.create(Array(ScalaTokenTypes.tIDENTIFIER)))
  }
}




trait ScFunction extends ScParametrized with ScReferenceIdContainer {

  override def getNames() = {
    val children = childrenOfType[ScReferenceId](ScalaElementTypes.REFERENCE_SET)
    if (children != null) {
      children.toList
    } else {
      Nil: List[ScReferenceId]
    }
  }

  def getAllParams = (paramClauses.toList :\ (Nil: List[ScParam]))((y: ScParamClause, x: List[ScParam])=>
    y.params.toList ::: x)

  /**
  *  Return abstract types of all parameters
  */
  def getParameterTypes: List[AbstractType] = {
    getAllParams.map((param: ScParam) => param.paramType.getAbstractType)
  }

  /**
  *   Returns explicit type of variable, or null, if it is not specified
  */
  override def getExplicitType(id: ScReferenceId) = {
    val child = childSatisfyPredicateForASTNode((node: ASTNode) => node.getPsi.isInstanceOf[ScalaType])
    if (child != null) {
      new FunctionType(getParameterTypes, child.asInstanceOf[ScalaType].getAbstractType, null)
    } else {
      null
    }
  }
  /**
  *   Returns infered type of variable, or null in case of any problems with inference
  */
  override def getInferedType(id: ScReferenceId) = {
    val child = childSatisfyPredicateForPsiElement((el: PsiElement) => el.isInstanceOf[IScalaExpression])
    if (child != null) {
      import org.jetbrains.plugins.scala.lang.typechecker._
      new FunctionType(getParameterTypes, (new ScalaTypeChecker).getTypeByTerm(child), null)
    } else {
      null
    }
  }

/*
  override def getAbstractType(id: ScReferenceId): FunctionType = {
    if (getExplicitType(id) != null) {
      getExplicitType(id)
    } else {
      getInferedType(id)
    }
  }
*/

  def canBeOverridenBy(newFun: ScFunction): Boolean = {
    if (getFunctionName == null || getAbstractType == null || newFun.getAbstractType == null) {
      return false
    } else
    { //Console.println("new: "+newFun.getAbstractType.getRepresentation + " old: "+ this.getAbstractType.getRepresentation)
      if (this.getFunctionName.equals(newFun.getFunctionName)  &&
      newFun.getAbstractType.conformsTo(this.getAbstractType)) {
        return true
      } else {
        false
      }
    }
  }

  def getAbstractType: FunctionType = {null}//getAbstractType(null)

  private def isManyParamClauses = (getChild(ScalaElementTypes.PARAM_CLAUSES) != null)
  private def getParamClausesNode: ScParamClauses = getChild(ScalaElementTypes.PARAM_CLAUSES).asInstanceOf[ScParamClauses]

  def paramClauses: Iterable[ScParamClause] = {
    if (isManyParamClauses) return getParamClausesNode.paramClauses
    childrenOfType[ScParamClause](TokenSet.create(Array(ScalaElementTypes.PARAM_CLAUSE)))
  }

  def getFunctionName = {
    val names = getNames
    if (names != null) {
      names.head.getText
    } else {
      "unnamedFunction"
    }
  }

  override def getIcon(flags: Int) = Icons.METHOD
}

/***************** definition ***********************/

trait ScDefinition extends ScTemplateStatement {
  override def toString: String = "definition"

  [Nullable]
  def getExpr: ScalaPsiElement = {
    getLastChild match {
      case under: ScalaPsiElement if (ScalaTokenTypes.tUNDER.equals(under.getNode.getElementType)) => under
      case expr: ScalaPsiElement => expr
      case _ => null
    }
  }
}

trait ScParametrized extends ScTemplateStatement {
  private def isTypeParamClause = (e: PsiElement) => e.isInstanceOf[ScTypeParamClause]
  def getTypeParamClause: ScTypeParamClause = childSatisfyPredicateForPsiElement(isTypeParamClause).asInstanceOf[ScTypeParamClause]
}


/**
*  Function definition
*/
case class ScFunctionDefinition(node: ASTNode) extends ScalaPsiElementImpl(node)
with ScFunction with ScDefinition with IfElseIndent with LocalContainer {

  def getParameters = (paramClauses :\ (Nil: List[ScParam]))((y: ScParamClause, x: List[ScParam]) =>
    y.params.toList ::: x)

  import com.intellij.psi.scope._
  override def getVariable(processor: PsiScopeProcessor,
      substitutor: PsiSubstitutor): Boolean = {

    // Scan for parameters
    for (val paramDef <- getParameters; paramDef.getTextOffset <= varOffset) {
      if (paramDef != null && ! processor.execute(paramDef, substitutor)) {
        return false
      }
    }
    return true
  }

  /**
  *  Process declarations of parameters
  */
  override def processDeclarations(processor: PsiScopeProcessor,
      substitutor: PsiSubstitutor,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve.processors._

    if (processor.isInstanceOf[ScalaLocalVariableResolveProcessor]){
        this.varOffset = processor.asInstanceOf[ScalaLocalVariableResolveProcessor].offset
      getVariable(processor, substitutor)
    } else true
  }

  override def toString: String = "function" + " " + super.toString

}



/************** supplementary constructor ***************/

case class ScSelfInvocation(node: ASTNode) extends ScalaPsiElementImpl(node) {
  override def toString: String = "self invocation"
}

case class ScConstrExpr(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString: String = "constructor expression"
}

case class ScSupplementaryConstructor(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunction with ScDefinition with IfElseIndent {
  override def toString: String = "supplementary constructor"

  override def names: Iterable[PsiElement] = childrenOfType[PsiElement](TokenSet.create(Array(ScalaTokenTypes.kTHIS)))
}


case class ScTypeAliasDefinition(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDefinition with ScTemplateStatement with ScParametrized{
  override def toString: String = "type" + " " + super.toString

  def nameNode = {
    def isName = (elementType: IElementType) => (elementType == ScalaTokenTypes.tIDENTIFIER)
    childSatisfyPredicateForElementType(isName)
  }

  override def getName = nameNode.getText

}


/***************** declaration ***********************/

trait Declaration extends ScTemplateStatement {
  override def toString: String = "declaration"
}

case class ScFunctionDeclaration(node: ASTNode) extends ScalaPsiElementImpl(node) with Declaration with ScFunction {
  override def toString: String = "function" + " " + super.toString

  override def getIcon(flags: Int) = Icons.FUNCTION
}

case class ScTypeDeclaration(node: ASTNode) extends ScalaPsiElementImpl(node) with Declaration with ScTemplateStatement {
  override def toString: String = "type" + " " + super.toString

  def nameNode = {
    def isName = (elementType: IElementType) => (elementType == ScalaTokenTypes.tIDENTIFIER)
    childSatisfyPredicateForElementType(isName)
  }

  override def getName = nameNode.getText
}

/************** function signature *********************/

/* class ScFunctionSignature (node : ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString: String = "function signature"
}*/

/****************** variable ************************/

class ScIdentifierList(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString: String = "list of identifiers"
}

/**
*   Reference id in instances of ScReferenceIdContainer
*/
class ScReferenceId(node: ASTNode) extends ScalaPsiElementImpl (node){

  def getType: AbstractType = {
    def walkUp(elem: PsiElement): PsiElement =  {
      if (elem.isInstanceOf[ScReferenceIdContainer] || elem == null) {
        return elem
      } else {
        return walkUp(elem.getParent)
      }
    }

    val typedParent = walkUp(this)
    if (typedParent == null) {
      return null
    } else {
      if (typedParent.asInstanceOf[ScReferenceIdContainer].getExplicitType(this) != null) {
        typedParent.asInstanceOf[ScReferenceIdContainer].getExplicitType(this)
      } else {
        typedParent.asInstanceOf[ScReferenceIdContainer].getInferedType(this)
      }
    }

  }

  override def getName = getText

  override def toString: String = "Reference identifier"
}

/****************** pattern2List  ************************/

class ScPattern2List(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString: String = "list of pattern2"
}
