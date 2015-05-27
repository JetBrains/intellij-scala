package org.jetbrains.plugins.scala.debugger.smartStepInto

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.{BasicStepMethodFilter, BreakpointStepMethodFilter, DebugProcessImpl}
import com.intellij.psi.PsiMethod
import com.intellij.util.Range
import com.sun.jdi.{Location, Method}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod

import scala.collection.JavaConverters._

/**
 * @author Nikolay.Tropin
 */
class ScalaBreakpointMethodFilter(fun: PsiMethod, exprLines: Range[Integer])
        extends BasicStepMethodFilter(fun, exprLines) with BreakpointStepMethodFilter {

  private var firstStatementPosition: Option[SourcePosition] = None
  private var lastStatementPosition: Option[SourcePosition] = None

  init()
  
  private def init(): Unit = {
    fun match {
      case funDef: ScFunctionDefinition =>
        funDef.body match {
          case Some(b: ScBlock) =>
            val stmts = b.statements
            if (stmts.nonEmpty) {
              firstStatementPosition = SourcePosition.createFromElement(stmts.head).toOption
              lastStatementPosition = SourcePosition.createFromElement(stmts.last).toOption
            }
          case Some(e: ScExpression) =>
            firstStatementPosition = SourcePosition.createFromElement(e).toOption
            lastStatementPosition = firstStatementPosition
          case _ =>
        }
      case pc: ScPrimaryConstructor => setPositionsForTemplate(pc.containingClass)
      case fake: FakePsiMethod =>
        fake.navElement match {
          case newTp: ScNewTemplateDefinition => setPositionsForTemplate(newTp)
          case _ =>
        }
    }
  }

  private def setPositionsForTemplate(tp: ScTemplateDefinition) = {
    val valsAndExprs = tp.extendsBlock.templateBody.toSeq
            .flatMap(tb => tb.members.filterNot(_.isInstanceOf[ScMethodLike]) ++ tb.exprs)
            .sortBy(_.getTextRange.getStartOffset)
    if (valsAndExprs.nonEmpty) {
      firstStatementPosition = valsAndExprs.headOption.map(SourcePosition.createFromElement)
      lastStatementPosition = valsAndExprs.lastOption.map(SourcePosition.createFromElement)
    }
  }

  override def locationMatches(process: DebugProcessImpl, location: Location): Boolean = {
    def signatureMatches(method: Method): Boolean = {
      if (myTargetMethodSignature == null) return true
      val expectedSignature = myTargetMethodSignature.getName(process)
      if (expectedSignature == method.signature) return true
      val sameNameMethods = method.declaringType().methodsByName(method.name()).asScala
      sameNameMethods.exists { candidate =>
        candidate != method && candidate.isBridge && expectedSignature == candidate.signature()
      }
    }

    val method: Method = location.method
    getMethodName == method.name && signatureMatches(method)
  }

  override def getBreakpointPosition: SourcePosition = firstStatementPosition.orNull

  override def getLastStatementLine: Int = lastStatementPosition.map(_.getLine).getOrElse(-1)
}
