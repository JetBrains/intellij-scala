package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import stubs.ScFunctionStub
import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.psi.scope._
import types.ScType
import types.result.{TypingContext, Success, TypeResult}
import api.base.types.ScTypeElement
import api.ScalaElementVisitor

/**
 * @author Jason Zaugg
 */
class ScMacroDefinitionImpl extends ScFunctionImpl with ScMacroDefinition {
  def this(node: ASTNode) = {
    this(); setNode(node)
  }

  def this(stub: ScFunctionStub) = {
    this(); setStub(stub); setNode(null)
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    //process function's parameters for dependent method types, and process type parameters
    if (!super[ScFunctionImpl].processDeclarations(processor, state, lastParent, place)) return false

    // TODO (?)
    true
  }

  override def toString: String = "ScMacroDefinition: " + name

  def returnTypeInner: TypeResult[ScType] = returnTypeElement match {
    case None => Success(types.Any, Some(this)) // TODO look up type from the macro impl.
    case Some(rte: ScTypeElement) => rte.getType(TypingContext.empty)
  }

  override def hasAssign: Boolean = true

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitMacroDefinition(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitMacroDefinition(this)
      case _ => super.accept(visitor)
    }
  }
}
