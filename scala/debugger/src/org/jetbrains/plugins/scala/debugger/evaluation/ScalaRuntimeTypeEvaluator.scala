package org.jetbrains.plugins.scala
package debugger.evaluation

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.codeinsight.RuntimeTypeEvaluator
import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator
import com.intellij.debugger.engine.evaluation.{CodeFragmentKind, EvaluationContextImpl, TextWithImportsImpl}
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.sun.jdi.{ClassType, ReferenceType, Value}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.debugger.evaluation.ReadActions.inNonBlockingReadAction
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaRuntimeTypeEvaluator._
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.lang.psi.ElementScope

import scala.jdk.CollectionConverters._

abstract class ScalaRuntimeTypeEvaluator(@Nullable editor: Editor, expression: PsiElement, context: DebuggerContextImpl, indicator: ProgressIndicator)
        extends RuntimeTypeEvaluator(editor, expression, context, indicator) {

  override def evaluate(evaluationContext: EvaluationContextImpl): PsiType = {
    val project: Project = evaluationContext.getProject
    val process = context.getDebugProcess
    if (process == null) return null

    // Building the evaluator resolves the expression, which can take seconds in a large project. In a plain read
    // action that makes every write action (e.g. typing in the editor) wait until it is done (SCL-26185).
    // A non-blocking read action is cancelled and restarted when a write action is pending, and the indicator lets
    // the requester drop the computation once it is no longer interested in the result.
    def nonBlocking[T](body: => T): T = inNonBlockingReadAction(expireWhen = () => !process.isAttached, Some(indicator))(body)

    val evaluator: ExpressionEvaluator = nonBlocking {
      val textWithImports = new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, expression.getText)
      val codeFragment = new ScalaCodeFragmentFactory().createPsiCodeFragment(textWithImports, expression, project)
      ScalaEvaluatorBuilder.build(codeFragment, ContextUtil.getSourcePosition(evaluationContext))
    }
    val value: Value = evaluator.evaluate(evaluationContext)
    if (value == null) throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.surrounded.expression.null"))

    // talk to the debuggee first, so that only the PSI lookup runs under the read lock
    val candidates = castableTypeCandidates(value)
    nonBlocking {
      val elementScope = ElementScope(project, process.getSearchScope)
      candidates.iterator
        .flatMap(DebuggerUtil.findPsiClassByQName(_, elementScope))
        .nextOption()
        .map(new PsiImmediateClassType(_, PsiSubstitutor.EMPTY))
        .orNull
    }
  }
}

object ScalaRuntimeTypeEvaluator {
  private val stdTypeNames = Set("java.lang.Object", "scala.Any", "scala.AnyRef", "scala.AnyVal")

  /**
   * The runtime type of `value` followed by the supertypes worth offering as a cast target when the type itself has
   * no PSI class: its superclass unless that is a standard one, otherwise its interfaces. Talks to the debuggee only.
   */
  private def castableTypeCandidates(value: Value): Seq[ReferenceType] = {
    val unwrapped = DebuggerUtil.unwrapScalaRuntimeRef(value)
    val jdiType = unwrapped.asInstanceOf[Value].`type`

    val baseTypes: Seq[ReferenceType] = jdiType match {
      case classType: ClassType =>
        val superclass = classType.superclass
        if (superclass != null && !stdTypeNames.contains(superclass.name)) Seq(superclass)
        else classType.interfaces.asScala.toSeq
      case _ => Seq.empty
    }

    (jdiType +: baseTypes).collect { case refType: ReferenceType => refType }
  }
}
