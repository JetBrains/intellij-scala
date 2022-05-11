package org.jetbrains.plugins.scala
package debugger
package evaluation
package evaluator

import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, TypeEvaluator}
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.sun.jdi._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

private[evaluation] final class LambdaExpressionEvaluator private(className: String, classText: String, classParams: Seq[Evaluator], psiContext: PsiElement) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): ObjectReference = {
    val process = context.getDebugProcess
    val project = context.getProject

    val autoLoadContext = context.withAutoLoadClasses(true)
    val classLoader = ClassLoadingUtils.getClassLoader(autoLoadContext, process)
    autoLoadContext.setClassLoader(classLoader)

    val helper = EvaluatorCompileHelper.implementations.headOption.getOrElse {
      ScalaEvaluatorCompileHelper.instance(project)
    }

    val module = inReadAction(ModuleUtilCore.findModuleForPsiElement(psiContext))
    val compiled = helper.compile(classText, module).filter(_._1.getName.endsWith(s"$className.class"))

    compiled.foreach { case (file, name) =>
      val bytes = Files.readAllBytes(file.toPath)
      ClassLoadingUtils.defineClass(name, bytes, autoLoadContext, process, classLoader)
      process.findClass(autoLoadContext, name, classLoader)
    }

    new NewLambdaClassInstanceEvaluator(
      new TypeEvaluator(JVMNameUtil.getJVMRawText(className)),
      classParams: _*
    ).evaluate(context)
  }
}

private[evaluation] object LambdaExpressionEvaluator {
  private[this] val counter: AtomicInteger = new AtomicInteger(0)

  private[this] val captureCounter: AtomicInteger = new AtomicInteger(0)

  def fromFunctionExpression(fun: ScFunctionExpr, psiContext: PsiElement): LambdaExpressionEvaluator = {
    val count = counter.incrementAndGet()
    val className = s"DebuggerLambdaExpression$$$count"

    val params = fun.parameters.map(p => s"${p.name}: ${p.`type`().getOrAny.canonicalText}").mkString(", ")
    val funType = fun.`type`().getOrAny.canonicalText
    val expr = fun.result.get
    val retType = expr.`type`().getOrAny.canonicalText

    val (rewritten, calc) = calculateClassParams(expr)
    val classParams = calc.map(_._1).mkString(", ")

    val classText =
      s"""class $className($classParams) extends ($funType) {
         |  override def apply($params): $retType = {
         |    ${rewritten.getText}
         |  }
         |
         |  private def invokeMethod(instance: Any, methodName: String): Any = ???
         |
         |  private def readField(instance: Any, fieldName: String): Any = ???
         |}
         |""".stripMargin.trim

    new LambdaExpressionEvaluator(className, classText, calc.map(_._2), psiContext)
  }

  private def calculateClassParams(expression: ScExpression): (ScExpression, Seq[(String, Evaluator)]) = expression match {
    case ref: ScReferenceExpression =>
      ref.resolve() match {
        case InEvaluationExpression() => (expression, Seq.empty)
        case ExpressionEvaluatorBuilder.LocalVariable(name, tpe, scope) =>
          val eval = new LocalVariableEvaluator(name, scope)
          val param = s"$name: ${tpe.canonicalText}"
          (expression, Seq((param, eval)))
        case ExpressionEvaluatorBuilder.ClassMemberVariable(name, tpe, containingClass, jvmName, typeFilter) =>
          val eval = new StackWalkingThisEvaluator(jvmName, typeFilter)
          val count = captureCounter.incrementAndGet()
          val param = s"$$this$$$count: ${containingClass.getQualifiedName}"
          val copy = expression.copy().asInstanceOf[ScExpression]
          val rewritten = typeFilter match {
            case StackWalkingThisEvaluator.TypeFilter.ContainsField(_) =>
              createExpressionWithContextFromText(s"""readField($$this$$$count, "$name").asInstanceOf[${tpe.canonicalText}]""", copy, copy)
            case StackWalkingThisEvaluator.TypeFilter.ContainsMethod(_) =>
              createExpressionWithContextFromText(s"""invokeMethod($$this$$$count, "$name").asInstanceOf[${tpe.canonicalText}]""", copy, copy)
          }
          val replaced = copy.replaceExpression(rewritten, removeParenthesis = false)
          (replaced, Seq((param, eval)))
      }
  }

  private object InEvaluationExpression {
    def unapply(element: PsiElement): Boolean =
      element.getContainingFile.getVirtualFile.getName == "Dummy.scala"
  }
}
