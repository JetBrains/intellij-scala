package org.jetbrains.plugins.scala.lang.psi.impl.expressions
/**
* @author Ilya Sergey
* PSI implementation for auxiliary expressions
*/
import com.intellij.lang.ASTNode
import com.intellij.psi.tree._
import com.intellij.psi._
import com.intellij.psi.scope._

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.resolve.processors._

import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.psi.containers._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions.simpleExprs._

case class ScArgumentExprsImpl(node: ASTNode) extends ScExpr1Impl(node) with ContiniousIndent{

  def getArguments: List[IScalaExpression] = {
    val list = getChildren.elements.toList.filter((e: PsiElement) => e.isInstanceOf[ScNewTemplateDefinition] ||
    e.isInstanceOf[IScalaExpression]).map((e: PsiElement) => e.asInstanceOf[IScalaExpression])
    list
  }


  override def toString: String = "Argument expressions"
}

case class ScBlockExprImpl(node: ASTNode) extends ScExpr1Impl(node) with BlockedIndent{
  override def toString: String = "Block expression"

  //    def isExpr = (elementType : IElementType) => (ScalaElementTypes.EXPRESSION_BIT_SET.contains(elementType))
  //
  //    def getExpression : ScExpr1Impl = childSatisfyPredicate(isExpr).asInstanceOf[ScExpr1Impl]
}

case class ScResExprImpl(node: ASTNode) extends ScalaExpression (node) with ScBlock {
  override def toString: String = "Result expression"
  def getType(): PsiType = null
}

case class ScBlockStatImpl(node: ASTNode) extends ScExpr1Impl(node) {
  override def toString: String = "Common block statement"
}

case class ScErrorStatImpl(node: ASTNode) extends ScExpr1Impl(node) {
  override def toString: String = "Error statement"
}

/**
*  Implementation for bindings in closures
*/
case class ScBindingImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScReferenceIdContainer {
  import org.jetbrains.plugins.scala.lang.psi.impl.types._
  import org.jetbrains.plugins.scala.lang.typechecker.types._
  override def toString: String = "Binding"

  def getReferenceId = {
    childSatisfyPredicateForASTNode((node: ASTNode) => ScalaElementTypes.REFERENCE.equals(node.getElementType))
  }

  def getExplicitType: AbstractType = {
    val scalaType = childSatisfyPredicateForPsiElement((elem: PsiElement) => elem.isInstanceOf[ScalaType]).asInstanceOf[ScalaType]
    if (scalaType != null) {
      scalaType.getAbstractType
    } else {
      null
    }
  }

  override def getExplicitType(id: ScReferenceId): AbstractType = {
    if (getReferenceId.equals(id)) {
      val scalaType = childSatisfyPredicateForPsiElement((elem: PsiElement) => elem.isInstanceOf[ScalaType]).asInstanceOf[ScalaType]
      if (scalaType != null) {
        scalaType.getAbstractType
      } else {
        null
      }
    } else {
      null
    }
  }

  def getNames = {
    val children = allChildrenOfType[ScReferenceId](ScalaElementTypes.REFERENCE_SET)
    if (children != null) {
      children.toList
    } else {
      Nil: List[ScReferenceId]
    }
  }

}

class ScEnumeratorImpl(node: ASTNode) extends ScEnumeratorsImpl(node) {
  override def toString: String = "Enumerator"
}

case class ScEnumeratorsImpl(node: ASTNode) extends ScExpr1Impl(node) with ContiniousIndent{
  override def toString: String = "Enumerators"
}

case class ScGuardImpl(node: ASTNode) extends ScalaPsiElementImpl (node){
  override def toString: String = "Guard"
}

case class ScAnFunImpl(node: ASTNode) extends ScExpr1Impl(node) with IfElseIndent with LocalContainer{
  import com.intellij.psi.scope._
  import org.jetbrains.plugins.scala.lang.typechecker.types._

  /*
    override def processDeclarations(processor: PsiScopeProcessor,
        substitutor: PsiSubstitutor,
        lastParent: PsiElement,
        place: PsiElement): Boolean = {
      import org.jetbrains.plugins.scala.lang.resolve.processors._

      if (processor.isInstanceOf[ScalaLocalVariableResolveProcessor]){
          this.varOffset = processor.asInstanceOf[ScalaLocalVariableResolveProcessor].offset
        getBindingReferences(processor, substitutor)
      } else true
    }
  */

  override def getAbstractType = {
    val bindingTypes = getBindings.map((b: ScBindingImpl)=> b.getExplicitType)
    if (getExpression != null && bindingTypes != null) {
      new FunctionType(bindingTypes, getExpression.getAbstractType, null)
    } else {
      null
    }
  }

  def getExpression: ScalaExpression = childSatisfyPredicateForPsiElement((e: PsiElement) =>
    e.isInstanceOf[ScalaExpression]).asInstanceOf[ScalaExpression]

  def getBindings = childrenOfType[ScBindingImpl](TokenSet.create(Array(ScalaElementTypes.BINDING))).toList

  def getBindingReferences(processor: PsiScopeProcessor,
      substitutor: PsiSubstitutor): Boolean = {

    // Scan for parameters
    /*
        for (val binding <- getBindings; binding.getTextOffset <= varOffset) {
          if (binding != null && ! processor.execute(binding, substitutor)) {
            return false
          }
        }
    */
    return true
  }

  override def toString: String = "Anonymous function"

  def getType(): PsiType = null
}


/**
*   Main entity of code of block
*
*/
case class ScBlockImpl(node: ASTNode) extends ScalaPsiElementImpl (node)
with ScBlock with Importable with LocalContainer{
  override def toString: String = "Block"

  def getType(): PsiType = null

  def getTmplDefs = childrenOfType[ScalaPsiElementImpl](ScalaElementTypes.TMPL_DEF_BIT_SET)

/*
   override def processDeclarations(processor: PsiScopeProcessor,
       substitutor: PsiSubstitutor,
       lastParent: PsiElement,
       place: PsiElement): Boolean = {
     import org.jetbrains.plugins.scala.lang.resolve.processors._

     if (processor.isInstanceOf[ScalaClassResolveProcessor]) { // Get Class types
         this.canBeObject = processor.asInstanceOf[ScalaClassResolveProcessor].canBeObject
         this.offset = processor.asInstanceOf[ScalaClassResolveProcessor].offset
       getClazz(getTmplDefs, processor, substitutor)
     } else if (processor.isInstanceOf[ScalaLocalVariableResolveProcessor]){
       // Get Local variables
         this.varOffset = processor.asInstanceOf[ScalaLocalVariableResolveProcessor].offset
       getVariable(processor, substitutor)
     } else true
   }
*/
}


case class ScTupleImpl(node: ASTNode) extends ScalaPsiElementImpl (node){
override def toString: String = "Tuple"
def getType(): PsiType = null
}

