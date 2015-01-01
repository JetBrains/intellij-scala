package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, Unit}

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
    //process function's parameters for dependent method types, and process type parameters
    if (!super[ScFunctionImpl].processDeclarations(processor, state, lastParent, place)) return false

    //do not process parameters for default parameters, only for function body
    //processing parameters for default parameters in ScParameters
    val parameterIncludingSynthetic: Seq[ScParameter] = effectiveParameterClauses.flatMap(_.effectiveParameters)
    if (getStub == null) {
      body match {
        case Some(x) 
          if lastParent != null && 
            (!needCheckProcessingDeclarationsForBody || 
            x.getStartOffsetInParent == lastParent.getStartOffsetInParent) =>
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
  
  protected def needCheckProcessingDeclarationsForBody = true

  override def toString: String = "ScFunctionDefinition: " + name

  def returnTypeInner: TypeResult[ScType] = returnTypeElement match {
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

  override def getBody: FakePsiCodeBlock = body match {
    case Some(b) => new FakePsiCodeBlock(b) // Needed so that LineBreakpoint.canAddLineBreakpoint allows line breakpoints on one-line method definitions
    case None => null
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

  override def importantOrderFunction(): Boolean = {
    hasModifierProperty("implicit") && !hasExplicitType
  }
}
