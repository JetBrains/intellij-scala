package org.jetbrains.plugins.scala.debugger.smartStepInto

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine._
import com.intellij.psi.{PsiElement, PsiMethod}
import com.intellij.util.Range
import com.sun.jdi.{Location, Method}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.debugger.ScalaPositionManager
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.JavaConverters._

/**
 * @author Nikolay.Tropin
 */
class ScalaBreakpointMethodFilter(psiMethod: Option[PsiMethod],
                                  firstStatementPosition: Option[SourcePosition],
                                  lastStatementPosition: Option[SourcePosition],
                                  exprLines: Range[Integer])
        extends BreakpointStepMethodFilter {

  private val expectedSignature = psiMethod.map {
    case f: ScMethodLike => DebuggerUtil.getFunctionJVMSignature(f)
    case m => JVMNameUtil.getJVMSignature(m)
  }

  override def locationMatches(process: DebugProcessImpl, location: Location): Boolean = {
    def signatureMatches(method: Method): Boolean = {
      val expSign = expectedSignature match {
        case None => return true
        case Some(jvmSign) => jvmSign.getName(process)
      }
      if (expSign == method.signature) return true
      val sameNameMethods = method.declaringType().methodsByName(method.name()).asScala
      sameNameMethods.exists { candidate =>
        candidate != method && candidate.isBridge && expSign == candidate.signature()
      }
    }

    val method: Method = location.method
    psiMethod match {
      case None => //is created for fun expression
        method.name == "apply" || method.name.startsWith("apply$") || ScalaPositionManager.isIndyLambda(method)
      case Some(m) =>
        val javaName = inReadAction(if (m.isConstructor) "<init>" else ScalaNamesUtil.toJavaName(m.name))
        javaName == method.name && signatureMatches(method) && !ScalaPositionManager.shouldSkip(location, process)

    }
  }

  override def getBreakpointPosition: SourcePosition = firstStatementPosition.orNull

  override def getLastStatementLine: Int = lastStatementPosition.map(_.getLine).getOrElse(-1)

  override def getCallingExpressionLines: Range[Integer] = exprLines
}

object ScalaBreakpointMethodFilter {
  def from(elem: PsiElement, exprLines: Range[Integer]): Option[ScalaBreakpointMethodFilter] = elem match {
    case null => None
    case funDef: ScFunctionDefinition =>
      funDef.body match {
        case Some(b: ScBlock) => from(Some(funDef), b.statements, exprLines)
        case Some(e: ScExpression) =>  from(Some(funDef), Seq(e), exprLines)
        case _ => None
      }
    case pc: ScPrimaryConstructor =>
      val statements = stmtsForTemplate(pc.containingClass)
      from(Some(pc), statements, exprLines)
    case method@FakePsiMethod(newTp: ScNewTemplateDefinition) => from(Some(method), newTp +: stmtsForTemplate(newTp), exprLines)
    case _ => None
  }

  def from(psiMethod: Option[PsiMethod], stmts: Seq[ScBlockStatement], exprLines: Range[Integer]): Option[ScalaBreakpointMethodFilter] = {
    from(psiMethod, stmts.headOption, stmts.lastOption, exprLines)
  }

  def from(psiMethod: Option[PsiMethod], first: Option[PsiElement], last: Option[PsiElement], exprLines: Range[Integer]): Option[ScalaBreakpointMethodFilter] = {
    val firstPos = first.map(createSourcePosition)
    val lastPos = last.map(createSourcePosition)
    Some(new ScalaBreakpointMethodFilter(psiMethod, firstPos, lastPos, exprLines))
  }

  @Nullable
  private def stmtsForTemplate(tp: ScTemplateDefinition): Seq[ScBlockStatement] = {
    val membersAndExprs = tp.extendsBlock.templateBody.toSeq
            .flatMap(tb => tb.members ++ tb.exprs)
    membersAndExprs.collect {
      case x @ (_: ScPatternDefinition | _: ScVariableDefinition | _: ScExpression) => x.asInstanceOf[ScBlockStatement]
    }.sortBy(_.getTextOffset)
  }
  
  private def createSourcePosition(elem: PsiElement): SourcePosition = {
    val significantElem = DebuggerUtil.getSignificantElement(elem)
    SourcePosition.createFromElement(significantElem)
  }

}