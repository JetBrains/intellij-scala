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
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.sun.jdi.{ClassType, ReferenceType, Type, Value}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaRuntimeTypeEvaluator._
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions.{IteratorExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScType

import scala.jdk.CollectionConverters._

abstract class ScalaRuntimeTypeEvaluator(@Nullable editor: Editor, expression: PsiElement, context: DebuggerContextImpl, indicator: ProgressIndicator)
        extends RuntimeTypeEvaluator(editor, expression, context, indicator) {

  override def evaluate(evaluationContext: EvaluationContextImpl): PsiType = {
    val project: Project = evaluationContext.getProject
    val process = context.getDebugProcess
    if (process == null) return null

    val evaluator: ExpressionEvaluator = inReadAction {
      val textWithImports = new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, expression.getText)
      val codeFragment = new ScalaCodeFragmentFactory().createCodeFragment(textWithImports, expression, project)
      ScalaEvaluatorBuilder.build(codeFragment, ContextUtil.getSourcePosition(evaluationContext))
    }
    val value: Value = evaluator.evaluate(evaluationContext)
    if (value != null) {
      inReadAction {
        getCastableRuntimeType(value)(ElementScope(project, process.getSearchScope))
          .map(new PsiImmediateClassType(_, PsiSubstitutor.EMPTY)).orNull
      }
    } else throw EvaluationException(JavaDebuggerBundle.message("evaluation.error.surrounded.expression.null"))
  }
}

object ScalaRuntimeTypeEvaluator {

  val KEY: Key[ScExpression => ScType] = Key.create("SCALA_RUNTIME_TYPE_EVALUATOR")

  private val stdTypeNames = Set("java.lang.Object", "scala.Any", "scala.AnyRef", "scala.AnyVal")

  private def getCastableRuntimeType(value: Value)(implicit elementScope: ElementScope): Option[PsiClass] = {
    val unwrapped = DebuggerUtil.unwrapScalaRuntimeRef(value)
    val jdiType: Type = unwrapped.asInstanceOf[Value].`type`

    findPsiClass(jdiType).orElse {
      findBaseClass(jdiType)
    }
  }

  private def findBaseClass(jdiType: Type)(implicit elementScope: ElementScope): Option[PsiClass] = {
    jdiType match {
      case classType: ClassType =>
        val superclass: ClassType = classType.superclass
        if (superclass != null && !stdTypeNames.contains(superclass.name))
          findPsiClass(superclass)
        else
          classType.interfaces.iterator().asScala
            .flatMap(findPsiClass(_))
            .headOption
      case _ =>
        None
    }
  }

  private def findPsiClass(jdiType: Type)(implicit elementScope: ElementScope): Option[PsiClass] = {
    jdiType match {
      case refType: ReferenceType =>
        inReadAction(DebuggerUtil.findPsiClassByQName(refType, elementScope))
      case _ =>
        None
    }
  }
}
