package org.jetbrains.plugins.scala
package debugger
package evaluation
package evaluator

import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.sun.jdi._
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

private[evaluation] class CompilingEvaluator(psiContext: PsiElement, fragment: ScalaCodeFragment) extends Evaluator {
  override def evaluate(context: EvaluationContextImpl): Value = {
    val process = context.getDebugProcess
    val project = context.getProject

    val autoLoadContext = context.withAutoLoadClasses(true)
    val classLoader = ClassLoadingUtils.getClassLoader(autoLoadContext, process)
    autoLoadContext.setClassLoader(classLoader)

    val count = CompilingEvaluator.counter.incrementAndGet()

    val text =
      s"""object GeneratedObject$count {
         |  def method() = {
         |    ${indent(fragment.getText)}
         |  }
         |}""".stripMargin

    val helper = EvaluatorCompileHelper.implementations.headOption.getOrElse {
      ScalaEvaluatorCompileHelper.instance(project)
    }

    val module = inReadAction(ModuleUtilCore.findModuleForPsiElement(psiContext))
    val compiled = helper.compile(text, module).filter(_._1.getName.endsWith(".class"))

    compiled.foreach { case (file, name) =>
      val bytes = Files.readAllBytes(file.toPath)
      ClassLoadingUtils.defineClass(name, bytes, autoLoadContext, process, classLoader)
      process.findClass(autoLoadContext, name, classLoader)
    }

    val moduleEvaluator = ScalaFieldEvaluator(new ScalaTypeEvaluator(JVMNameUtil.getJVMRawText(s"GeneratedObject$count$$")), "MODULE$")
    val methodEvaluator = ScalaMethodEvaluator(moduleEvaluator, "method", null, Seq.empty)
    methodEvaluator.evaluate(autoLoadContext).asInstanceOf[Value]
  }

  private def indent(text: String): String =
    text.replace(System.lineSeparator(), s"${System.lineSeparator()}${" " * 4}")
}

private object CompilingEvaluator {
  private val counter: AtomicInteger = new AtomicInteger(0)
}
