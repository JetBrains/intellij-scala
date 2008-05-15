package org.jetbrains.plugins.scala.lang.psi.impl.statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScFunctionDefinitionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionDefinition {

  def getNameNode: ASTNode = {
    val name = node.findChildByType(ScalaTokenTypes.tIDENTIFIER)
    if (name == null) {
      if (node.getTreeParent.getElementType == ScalaElementTypes.TEMPLATE_BODY) {
        return node.getTreeParent.getTreeParent.getTreeParent.getPsi.asInstanceOf[ScTypeDefinition].getNameIdentifierScala.getNode
      }
      else return null
    } else return name
  }

  import com.intellij.psi.scope._

  def getVariable(processor: PsiScopeProcessor,
    substitutor: PsiSubstitutor): Boolean = {

    return true
  }

  override def processDeclarations(processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement,
    place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve._

    if (lastParent == getBody) {
      val ps = getParameters
      for (p <- ps) {
        if (!processor.execute(p, state)) return false
      }
      true
    }
    else true
  }

  override def getIcon(flags: Int) = Icons.FUNCTION

  override def toString: String = "ScFunctionDefinition"

  def getBody: PsiElement = findChildByClass(classOf[ScExpression])

  def getParameters: Seq[ScParam] = {
    val pcs = findChildByClass(classOf[ScParamClauses])
    if (pcs == null) pcs.getParameters else Seq.empty
  }

  def getParametersClauses: ScParamClauses = {
    findChildByClass(classOf[ScParamClauses])
  }
  def getReturnTypeNode: ScType = {
    findChildByClass(classOf[ScType])
  }
  def typeParametersClause: ScTypeParamClause = findChildByClass(classOf[ScTypeParamClause])

  def getFunctionsAndTypeDefs: Array[ScalaPsiElement] = {
    val res = new ArrayBuffer[ScalaPsiElement]
    for (child <- getBody.getChildren() if (child.isInstanceOf[ScTypeDefinition] || child.isInstanceOf[ScFunction]))
            res += child.asInstanceOf[ScalaPsiElement]
    return res.toArray
  }

  override def getTextOffset(): Int = getNameNode.getTextRange.getStartOffset

  override def getNavigationElement: PsiElement = getNameNode.getPsi

  def getTypeDefinitions(): Seq[ScTypeDefinition] = {
    if (getBody == null) return Seq.empty
    getBody.getChildren.flatMap(collectTypeDefs(_))
  }

  override def collectTypeDefs(child: PsiElement) = child match {
    case f: ScFunctionDefinition => f.getTypeDefinitions
    case t: ScTypeDefinition => List(t) ++ t.getTypeDefinitions
    case _ => Seq.empty
  }



}