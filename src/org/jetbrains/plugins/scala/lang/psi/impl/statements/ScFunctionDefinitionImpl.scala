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
import api.base.types.ScTypeElement
import collection.mutable.ArrayBuffer
import psi.controlFlow.Instruction
import psi.controlFlow.impl.ScalaControlFlowBuilder
import api.{ScalaElementVisitor, ScalaRecursiveElementVisitor}
import api.statements.params.ScParameter

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

class ScFunctionDefinitionImpl extends ScFunctionImpl with ScFunctionDefinition {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScFunctionStub) = {this (); setStub(stub); setNode(null)}

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    //process function's type parameters
    if (!super[ScFunctionImpl].processDeclarations(processor, state, lastParent, place)) return false

    val parameterIncludingSynthetic: Seq[ScParameter] = parameters ++ syntheticParamClause.map(_.parameters).getOrElse(Seq())
    if (getStub == null) {
      body match {
        case Some(x) if lastParent != null && x.getStartOffsetInParent == lastParent.getStartOffsetInParent =>
          for (p <- parameterIncludingSynthetic) {
            ProgressManager.checkCanceled
            if (!processor.execute(p, state)) return false
          }
        case _ =>
      }
    }
    else {
      if (lastParent != null && lastParent.getContext != lastParent.getParent) {
        for (p <- parameterIncludingSynthetic) {
          ProgressManager.checkCanceled
          if (!processor.execute(p, state)) return false
        }
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
    case Some(rte: ScTypeElement) => rte.getType(TypingContext.empty)
  }

  def body: Option[ScExpression] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScFunctionStub].getBodyExpression
    }
    findChild(classOf[ScExpression])
  }

  override def hasAssign: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScFunctionStub].hasAssign
    }
    assignment.isDefined
  }

  def assignment = Option(findChildByType(ScalaTokenTypes.tASSIGN))

  def removeAssignment() {
    body match {
      case Some(block: ScBlockExpr) => // do nothing
      case Some(exp: ScExpression) =>
        val block = ScalaPsiElementFactory.createBlockFromExpr(exp, exp.getManager)
        exp.replace(block)
      case _ =>
    }
    assignment.foreach(_.delete())
  }

  def getReturnUsages: Array[PsiElement] = {
    val res = new ArrayBuffer[PsiElement]
    body.foreach {
      _.depthFirst(!_.isInstanceOf[ScFunction]).foreach {
        case r: ScReturnStmt => res += r
        case _ =>
      }
    }
    body match {
      case Some(expr) => res ++= expr.calculateReturns
      case _ =>
    }
    res.filter(p => p.getContainingFile == getContainingFile).distinct.toArray
  }

  private var myControlFlow: Seq[Instruction] = null

  def getControlFlow(cached: Boolean) = {
    if (!cached || myControlFlow == null) body match {
      case Some(e) => {
        val builder = new ScalaControlFlowBuilder(null, null)
        myControlFlow = builder.buildControlflow(e)
      }
      case _ =>
    }
    myControlFlow
  }

  override def getBody: FakePsiCodeBlock = body match {
    case Some(b) => new FakePsiCodeBlock(b) // Needed so that LineBreakpoint.canAddLineBreakpoint allows line breakpoints on one-line method definitions
    case None => null
  }

  def isSecondaryConstructor: Boolean = name == "this"
}
