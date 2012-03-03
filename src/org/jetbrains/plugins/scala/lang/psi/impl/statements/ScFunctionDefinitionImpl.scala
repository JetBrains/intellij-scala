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
import caches.CachesUtil
import com.intellij.psi.util.PsiModificationTracker

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
    val isInMacroBody = body match {
      case Some(b) if isMacro =>
        b == place || b.isAncestorOf(place)
      case _ =>
        false
    }

    if (isInMacroBody) {
      return syntheticMacroFunctionPreamble match {
        case Some(block) => block.processDeclarations(processor, state, block.getLastChild, place)
        case None => true
      }
    }

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

  def isMacro: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScFunctionStub].isMacro
    }
    (this: ScalaPsiElement).findChildrenByType(ScalaTokenTypes.tIDENTIFIER).size == 2
  }

  private def syntheticMacroFunctionPreamble: Option[ScBlock] = {
    CachesUtil.get(this, CachesUtil.MACRO_FUNCTION_PREAMBLE,
      new CachesUtil.MyProvider(this, (p: ScFunctionDefinition) => syntheticMacroFunctionPreambleInner)
      (PsiModificationTracker.MODIFICATION_COUNT))
  }

  /**
   * The body of a macro sees an alternative list of parameters, the desugaring is
   * described [https://github.com/scala/scala/blob/master/src/compiler/scala/tools/nsc/typechecker/Macros.scala#L29].
   *
   * It's easier to process these as a block of code, rather than an alternative
   * parameter list.
   */
  private def syntheticMacroFunctionPreambleInner: Option[ScBlock] = {
    if (isMacro) {
      val c1 = "val _context: _root_.scala.reflect.macro.Context = null\n"
      val c2 = "val _this: _context.Tree = null\n"
      val c3 = if (typeParameters.isEmpty) None
      else {
        val s = typeParameters.map(tp => "val %s : _context.Tree = null\n".format(tp.name)).mkString("")
        Some(s)
      }
      val c4 = parameters.map(tp => "val %s : _context.Tree = null\n".format(tp.name)).mkString("")
      val paramClausesText: Seq[String] = Seq(c1, c2) ++ c3 ++ Seq(c4, "import _context._;")
      val blk = ScalaPsiElementFactory.createBlockExpressionWithoutBracesFromText(paramClausesText.mkString("\n"), getManager)
      blk.setContext(getContext, this)
      Some(blk)
    } else None
  }

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
