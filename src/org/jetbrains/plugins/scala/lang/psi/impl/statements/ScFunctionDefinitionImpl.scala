package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.stubs.StubElement
import lexer.ScalaTokenTypes
import stubs.elements.wrappers.DummyASTNode
import stubs.ScFunctionStub
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
import types.{ScType, Unit, Nothing, ScFunctionType}
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScFunctionDefinitionImpl extends ScFunctionImpl with ScFunctionDefinition {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScFunctionStub) = {this(); setStub(stub); setNode(null)}

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    //process function's type parameters
    if (!super[ScFunctionImpl].processDeclarations(processor, state, lastParent, place)) return false

    import org.jetbrains.plugins.scala.lang.resolve._
    if (getStub == null) {
      body match {
        case Some(x) if x == lastParent =>
          for (p <- parameters) {
            if (!processor.execute(p, state)) return false
          }
        case _ =>
      }
    }
    true
  }

  override def toString: String = "ScFunctionDefinition"

  import com.intellij.openapi.util.Key

  def returnType: ScType = {
    returnTypeElement match {
      case None => body match {
        case Some(b) => b.getType
        case _ => Unit
      }
      case Some(rte) => rte.getType
    }
  }

  def body: Option[ScExpression] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScFunctionStub].getBodyExpression
    }
    findChild(classOf[ScExpression])
  }
}