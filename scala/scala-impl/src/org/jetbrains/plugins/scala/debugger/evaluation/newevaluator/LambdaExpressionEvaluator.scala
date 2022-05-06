package org.jetbrains.plugins.scala
package debugger
package evaluation
package newevaluator

import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, TypeEvaluator}
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.sun.jdi._

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

private[evaluation] class LambdaExpressionEvaluator(
  functionType: String,
  params: Seq[(String, String)],
  body: String,
  compilationContext: PsiElement
) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): ObjectReference = {
    val process = context.getDebugProcess
    val project = context.getProject

    val autoLoadContext = context.withAutoLoadClasses(true)
    val classLoader = ClassLoadingUtils.getClassLoader(autoLoadContext, process)
    autoLoadContext.setClassLoader(classLoader)

    val count = LambdaExpressionEvaluator.counter.incrementAndGet()

    val className = s"DebuggerLambdaExpression$$$count"

    val paramsText = params.map { case (name, tpe) => s"$name: $tpe" }.mkString(", ")

    val returnType = functionType.split(" => ").last

    val text =
      s"""class $className extends ($functionType) {
         |  override def apply($paramsText): $returnType = {
         |    $body
         |  }
         |}
         |""".stripMargin.trim

    val helper = EvaluatorCompileHelper.implementations.headOption.getOrElse {
      ScalaEvaluatorCompileHelper.instance(project)
    }

    val module = inReadAction(ModuleUtilCore.findModuleForPsiElement(compilationContext))
    val compiled = helper.compile(text, module).filter(_._1.getName.endsWith(s"$className.class"))

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

private object LambdaExpressionEvaluator {
  private val counter: AtomicInteger = new AtomicInteger(0)
}
