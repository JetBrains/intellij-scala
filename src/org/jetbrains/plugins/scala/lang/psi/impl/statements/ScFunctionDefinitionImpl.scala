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
import api.ScalaElementVisitor
import api.statements.params.ScParameter
import api.base.ScReferenceElement
import extensions._
import api.toplevel.templates.ScTemplateBody
import api.toplevel.typedef.{ScTypeDefinition, ScObject}

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

class ScFunctionDefinitionImpl extends ScFunctionImpl with ScFunctionDefinition {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScFunctionStub) = {this (); setStub(stub); setNode(null)}

  def canBeTailRecursive = getParent match {
    case (_: ScTemplateBody) && Parent(Parent(owner: ScTypeDefinition)) =>
      val ownerModifiers = owner.getModifierList
      val methodModifiers = getModifierList
      owner.isInstanceOf[ScObject] ||
              ownerModifiers.has(ScalaTokenTypes.kFINAL) ||
              methodModifiers.has(ScalaTokenTypes.kPRIVATE) ||
              methodModifiers.has(ScalaTokenTypes.kFINAL)
    case _ => true
  }

  def hasTailRecursionAnnotation: Boolean =
    annotations.exists(_.typeElement.getType(TypingContext.empty)
            .map(_.canonicalText).exists(_ == "_root_.scala.annotation.tailrec"))

  def recursiveReferences: Seq[RecursiveReference] = {
    val resultExpressions = getReturnUsages

    def resultExpressionFor(ref: ScReferenceElement): PsiElement = {
      @scala.annotation.tailrec
      def resultExpressionForMethodCall(ref: PsiElement): PsiElement = ref match {
        case Parent(call: ScMethodCall) => resultExpressionForMethodCall(call)
        case Parent(ret: ScReturnStmt) => ret
        case _ => ref
      }

      ref match {
        case Parent(ret: ScReturnStmt) => ret
        case Parent(call: ScMethodCall) => resultExpressionForMethodCall(call)
        case it => it
      }
    }

    def expandIf(elem: PsiElement): Seq[PsiElement] = {
      elem match {
        case i: ScIfStmt if i.elseBranch.isEmpty =>
          i.thenBranch match {
            case Some(then) =>
              then.calculateReturns.flatMap(expandIf) :+ elem
            case _ => Seq(elem)
          }
        case _ => Seq(elem)
      }
    }
    val expressions = resultExpressions.flatMap(expandIf)
    for {
      ref <- depthFirst.filterByType(classOf[ScReferenceElement]).toSeq if ref.isReferenceTo(this)
    } yield {
      RecursiveReference(ref, expressions.contains(resultExpressionFor(ref)))
    }
  }

  def recursionType: RecursionType = {
    val references = recursiveReferences
    if (references.isEmpty) {
      RecursionType.NoRecursion
    } else {
      if (references.forall(_.isTailCall))
        RecursionType.TailRecursion
      else
        RecursionType.OrdinaryRecursion
    }
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    //process function's parameters for dependent method types, and process type parameters
    if (!super[ScFunctionImpl].processDeclarations(processor, state, lastParent, place)) return false

    //do not process parameters for default parameters, only for function body
    //processing parameters for default parameters in ScParameters
    val parameterIncludingSynthetic: Seq[ScParameter] = effectiveParameterClauses.flatMap(_.parameters)
    if (getStub == null) {
      body match {
        case Some(x) if lastParent != null && x.getStartOffsetInParent == lastParent.getStartOffsetInParent =>
          for (p <- parameterIncludingSynthetic) {
            ProgressManager.checkCanceled()
            if (!processor.execute(p, state)) return false
          }
        case _ =>
      }
    } else {
      if (lastParent != null && lastParent.getContext != lastParent.getParent) {
        for (p <- parameterIncludingSynthetic) {
          ProgressManager.checkCanceled()
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
    if (stub != null) stub.asInstanceOf[ScFunctionStub].getBodyExpression else findChild(classOf[ScExpression])
  }

  override def hasAssign: Boolean = {
    val stub = getStub
    if (stub != null) stub.asInstanceOf[ScFunctionStub].hasAssign else assignment.isDefined
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

  def getReturnUsages: Array[PsiElement] = body map (exp => {
    (exp.depthFirst(!_.isInstanceOf[ScFunction]).filter(_.isInstanceOf[ScReturnStmt]) ++ exp.calculateReturns).
      filter(_.getContainingFile == getContainingFile).toArray.distinct
  }) getOrElse (Array.empty[PsiElement])


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

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitFunctionDefinition(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitFunctionDefinition(this)
      case _ => super.accept(visitor)
    }
  }
}
