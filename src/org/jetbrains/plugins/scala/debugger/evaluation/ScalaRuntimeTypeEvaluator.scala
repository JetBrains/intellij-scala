package org.jetbrains.plugins.scala
package debugger.evaluation

import com.intellij.debugger.codeinsight.RuntimeTypeEvaluator
import org.jetbrains.annotations.Nullable
import com.intellij.openapi.editor.Editor
import com.intellij.psi._
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.debugger.engine.evaluation.{EvaluateExceptionUtil, EvaluationContextImpl}
import com.intellij.openapi.project.Project
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator
import com.intellij.debugger.{EvaluatingComputable, DebuggerBundle, DebuggerInvocationUtil}
import com.intellij.debugger.engine.ContextUtil
import com.sun.jdi.{ClassType, Type, Value}
import ScalaRuntimeTypeEvaluator._
import com.intellij.openapi.application.{ReadAction, AccessToken}
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import com.intellij.codeInsight.completion.CompletionParameters
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.ScType.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

/**
 * Nikolay.Tropin
 * 8/8/13
 */
abstract class ScalaRuntimeTypeEvaluator(@Nullable editor: Editor, expression: PsiElement, context: DebuggerContextImpl, indicator: ProgressIndicator)
        extends RuntimeTypeEvaluator(editor, expression, context, indicator) {

  override def evaluate(evaluationContext: EvaluationContextImpl): PsiClass = {
    val project: Project = evaluationContext.getProject

    val evaluator: ExpressionEvaluator = DebuggerInvocationUtil.commitAndRunReadAction(project, new EvaluatingComputable[ExpressionEvaluator] {
      def compute: ExpressionEvaluator = {
        ScalaEvaluatorBuilder.build(myElement, ContextUtil.getSourcePosition(evaluationContext))
      }
    })
    val value: Value = evaluator.evaluate(evaluationContext)
    if (value != null) {
      getCastableRuntimeType(project, value)
    } else throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.surrounded.expression.null"))
  }
}

object ScalaRuntimeTypeEvaluator {

  val KEY: Key[(ScExpression, CompletionParameters) => ScType] = Key.create("SCALA_RUNTIME_TYPE_EVALUATOR")

  def getCastableRuntimeType(project: Project, value: Value): PsiClass = {
    val jdiType: Type = DebuggerUtil.unwrapScalaRuntimeObjectRef(value).asInstanceOf[Value].`type`
    var psiClass: PsiClass = findPsiClass(project, jdiType)
    if (psiClass != null) {
      return psiClass
    }
    jdiType match {
      case classType: ClassType =>
        val superclass: ClassType = classType.superclass
        val stdTypeNames = Seq("java.lang.Object", "scala.Any", "scala.AnyRef", "scala.AnyVal")
        if (superclass != null && !stdTypeNames.contains(superclass.name)) {
          psiClass = findPsiClass(project, superclass)
          if (psiClass != null) {
            return psiClass
          }
        }
        import scala.collection.JavaConversions._
        classType.interfaces.map(findPsiClass(project, _)).find(_ != null).getOrElse(null)
      case _ => null
    }
  }

  private def findPsiClass(project: Project, jdiType: Type): PsiClass = {
    val token: AccessToken = ReadAction.start
    try {
      new ScalaPsiManager(project).getCachedClass(GlobalSearchScope.allScope(project), jdiType.name())
    }
    finally {
      token.finish()
    }
  }

  def isSubtypeable(scType: ScType): Boolean = {
    scType match {
      case ExtractClass(psiClass) =>
        psiClass match {
          case _: ScObject => false
          case owner: ScModifierListOwner => !owner.hasFinalModifier
          case _ if scType.isInstanceOf[PsiPrimitiveType] => false
          case _ => !psiClass.hasModifierProperty(PsiModifier.FINAL)
        }
      case _ => false
    }
  }
}
