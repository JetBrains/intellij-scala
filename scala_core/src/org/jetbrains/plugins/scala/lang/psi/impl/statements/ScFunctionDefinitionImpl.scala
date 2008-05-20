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
import com.intellij.psi.scope._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScFunctionDefinitionImpl(node: ASTNode) extends ScFunctionImpl(node) with ScFunctionDefinition {

  def getVariable(processor: PsiScopeProcessor, substitutor: PsiSubstitutor): Boolean = true

  override def processDeclarations(processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement,
    place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve._

    if (lastParent == getBodyExpr) {
      val ps = getParameters
      for (p <- ps) {
        if (!processor.execute(p, state)) return false
      }
      true
    }
    else true
  }

  override def toString: String = "ScFunctionDefinition"

  def getBodyExpr = findChildByClass(classOf[ScExpression])

  def getFunctionsAndTypeDefs = for (child <- getBodyExpr.getChildren() if (child.isInstanceOf[ScTypeDefinition] || child.isInstanceOf[ScFunction]))
          yield child.asInstanceOf[ScalaPsiElement]

  override def getTextOffset(): Int = getId.getTextRange.getStartOffset

  def getTypeDefinitions(): Seq[ScTypeDefinition] = {
    if (getBodyExpr == null) return Seq.empty
    getBodyExpr.getChildren.flatMap(collectTypeDefs(_))
  }

  override def collectTypeDefs(child: PsiElement) = child match {
    case f: ScFunctionDefinition => f.getTypeDefinitions
    case t: ScTypeDefinition => List(t) ++ t.getTypeDefinitions
    case _ => Seq.empty
  }



}