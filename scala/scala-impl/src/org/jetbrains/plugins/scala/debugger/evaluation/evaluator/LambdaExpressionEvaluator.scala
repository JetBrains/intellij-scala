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
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

private[evaluation] final class LambdaExpressionEvaluator private(className: String, classText: String, psiContext: PsiElement) extends Evaluator {
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

    new NewClassInstanceEvaluator(
      new TypeEvaluator(JVMNameUtil.getJVMRawText(className)),
      JVMNameUtil.getJVMRawText("()V")
    ).evaluate(context)
  }
}

private[evaluation] object LambdaExpressionEvaluator {
  private[this] val counter: AtomicInteger = new AtomicInteger(0)

  def fromFunctionExpression(fun: ScFunctionExpr, psiContext: PsiElement): LambdaExpressionEvaluator = {
    val count = counter.incrementAndGet()
    val className = s"DebuggerLambdaExpression$$$count"

    val params = fun.parameters.map(p => s"${p.name}: ${p.`type`().getOrAny.canonicalText}").mkString(", ")
    val funType = fun.`type`().getOrAny.canonicalText
    val expr = fun.result.get
    val retType = expr.`type`().getOrAny.canonicalText

    val classText =
      s"""class $className extends ($funType) {
         |  override def apply($params): $retType = {
         |    ${expr.getText}
         |  }
         |}
         |""".stripMargin.trim

    new LambdaExpressionEvaluator(className, classText, psiContext)
  }
}
