package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.stubs.StubElement
import lexer.ScalaTokenTypes
import stubs.elements.wrappers.DummyASTNode
import stubs.ScFunctionStub
import types.{Unit, Nothing, ScFunctionType}
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import typedef._
import packaging.ScPackaging
import com.intellij.psi.scope._
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScFunctionDefinitionImpl(node: ASTNode) extends ScFunctionImpl (node) with ScFunctionDefinition {
  def this(stub: ScFunctionStub) = {
    this(DummyASTNode)
    setStub(stub)
    setNode(node)
  }


  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    //process function's type parameters
    if (!super[ScFunctionImpl].processDeclarations(processor, state, lastParent, place)) return false

    import org.jetbrains.plugins.scala.lang.resolve._

    body match {
      case Some(x) if x == lastParent =>
        for (p <- parameters) {
          if (!processor.execute(p, state)) return false
        }
      case _ => 
    }
    
    true
  }

  override def toString: String = "ScFunctionDefinition"

  import com.intellij.openapi.util.Key

  def returnType = returnTypeElement match {
    case None => if (findChildByType(ScalaTokenTypes.tASSIGN) != null) (body match {
      case Some(b) => b.getType
      case _ => Nothing
    }) else Unit
    case Some(rte) => rte.getType
  }
}