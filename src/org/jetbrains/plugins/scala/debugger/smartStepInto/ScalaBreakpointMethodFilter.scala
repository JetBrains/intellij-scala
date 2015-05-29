package org.jetbrains.plugins.scala.debugger.smartStepInto

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine._
import com.intellij.psi.{PsiElement, PsiMethod}
import com.intellij.util.Range
import com.sun.jdi.{Location, Method}
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
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

  private val expectedSignature = psiMethod.map(JVMNameUtil.getJVMSignature)

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
        method.name == "apply" || method.name.startsWith("apply$")
      case Some(m) =>
        val javaName = if (m.isConstructor) "<init>" else ScalaNamesUtil.toJavaName(m.name)
        javaName == method.name && signatureMatches(method)

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
    case pc: ScPrimaryConstructor => from(Some(pc), stmtsForTemplate(pc.containingClass), exprLines)
    case fake: FakePsiMethod =>
      fake.navElement match {
        case newTp: ScNewTemplateDefinition => from(Some(fake), stmtsForTemplate(newTp), exprLines)
        case _ => None
      }
    case _ => None
  }

  def from(psiMethod: Option[PsiMethod], stmts: Seq[ScBlockStatement], exprLines: Range[Integer]): Option[ScalaBreakpointMethodFilter] = {
    val firstPos = stmts.headOption.map(SourcePosition.createFromElement)
    val lastPos = stmts.lastOption.map(SourcePosition.createFromElement)
    Some(new ScalaBreakpointMethodFilter(psiMethod, firstPos, lastPos, exprLines))
  }

  private def stmtsForTemplate(tp: ScTemplateDefinition): Seq[ScBlockStatement] = {
    val membersAndExprs = tp.extendsBlock.templateBody.toSeq
            .flatMap(tb => tb.members ++ tb.exprs)
    membersAndExprs.collect {
      case x @ (_: ScPatternDefinition | _: ScVariableDefinition | _: ScExpression) => x.asInstanceOf[ScBlockStatement]
    }.sortBy(_.getTextOffset)
  }

}
