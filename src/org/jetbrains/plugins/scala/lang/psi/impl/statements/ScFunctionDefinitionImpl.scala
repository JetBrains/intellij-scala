package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import lexer.ScalaTokenTypes
import stubs.ScFunctionStub
import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.scope._
import types.{ScType, Unit}
import types.result.{TypingContext, Success, TypeResult}
import com.intellij.openapi.progress.ProgressManager

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

class ScFunctionDefinitionImpl extends ScFunctionImpl with ScFunctionDefinition {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScFunctionStub) = {this (); setStub(stub); setNode(null)}

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    //process function's type parameters
    if (!super[ScFunctionImpl].processDeclarations(processor, state, lastParent, place)) return false

    if (getStub == null) {
      body match {
        case Some(x) if x == lastParent =>
          for (p <- parameters) {
            ProgressManager.checkCanceled
            if (!processor.execute(p, state)) return false
          }
        case _ =>
      }
    }
    true
  }

  override def toString: String = "ScFunctionDefinition"

  def returnType: TypeResult[ScType] = returnTypeElement match {
    case None if !hasAssign => Success(Unit, Some(this))
    case None => body match {
      case Some(b) => b.getType(TypingContext.empty)
      case _ => Success(Unit, Some(this))
    }
    case Some(rte) => rte.getType(TypingContext.empty)
  }

  def body: Option[ScExpression] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScFunctionStub].getBodyExpression
    }
    findChild(classOf[ScExpression])
  }

  def hasAssign: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScFunctionStub].hasAssign
    }
    findChildByType(ScalaTokenTypes.tASSIGN) != null
  }
}
