package org.jetbrains.plugins.scala
package debugger.evaluation

import com.intellij.debugger.codeinsight.RuntimeTypeEvaluator
import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator
import com.intellij.debugger.engine.evaluation.{CodeFragmentKind, EvaluationContextImpl, TextWithImportsImpl}
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.{DebuggerBundle, DebuggerInvocationUtil, EvaluatingComputable}
import com.intellij.openapi.application.{AccessToken, ReadAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.intellij.psi.search.GlobalSearchScope
import com.sun.jdi.{ClassType, Type, Value}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaRuntimeTypeEvaluator._
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, TypeSystem}

/**
 * Nikolay.Tropin
 * 8/8/13
 */
abstract class ScalaRuntimeTypeEvaluator(@Nullable editor: Editor, expression: PsiElement, context: DebuggerContextImpl, indicator: ProgressIndicator)
        extends RuntimeTypeEvaluator(editor, expression, context, indicator) {

  override def evaluate(evaluationContext: EvaluationContextImpl): PsiType = {
    val project: Project = evaluationContext.getProject

    val evaluator: ExpressionEvaluator = DebuggerInvocationUtil.commitAndRunReadAction(project, new EvaluatingComputable[ExpressionEvaluator] {
      def compute: ExpressionEvaluator = {
        val textWithImports = new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, expression.getText)
        val codeFragment = new ScalaCodeFragmentFactory().createCodeFragment(textWithImports, expression, project)
        ScalaEvaluatorBuilder.build(codeFragment, ContextUtil.getSourcePosition(evaluationContext))
      }
    })
    val value: Value = evaluator.evaluate(evaluationContext)
    if (value != null) {
      inReadAction {
        Option(getCastableRuntimeType(project, value)).map(new PsiImmediateClassType(_, PsiSubstitutor.EMPTY)).orNull
      }
    } else throw EvaluationException(DebuggerBundle.message("evaluation.error.surrounded.expression.null"))
  }
}

object ScalaRuntimeTypeEvaluator {

  val KEY: Key[ScExpression => ScType] = Key.create("SCALA_RUNTIME_TYPE_EVALUATOR")

  def getCastableRuntimeType(project: Project, value: Value): PsiClass = {
    val unwrapped = DebuggerUtil.unwrapScalaRuntimeObjectRef(value)
    val jdiType: Type = unwrapped.asInstanceOf[Value].`type`
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
        classType.interfaces.map(findPsiClass(project, _)).find(_ != null).orNull
      case _ => null
    }
  }

  private def findPsiClass(project: Project, jdiType: Type): PsiClass = {
    val token: AccessToken = ReadAction.start
    try {
      ScalaPsiManager.instance(project).getCachedClass(GlobalSearchScope.allScope(project), jdiType.name()).orNull
    }
    finally {
      token.finish()
    }
  }

  def isSubtypeable(scType: ScType)
                   (implicit typeSystem: TypeSystem): Boolean = {
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
