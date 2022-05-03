package org.jetbrains.plugins.scala
package debugger
package evaluation
package evaluator

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightUtilCore.findElementInRange
import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.Evaluator
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile, PsiFileFactory}
import com.sun.jdi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockStatement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.util.IndentUtil

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec

private[evaluation] class CompilingEvaluator(psiContext: PsiElement, fragment: ScalaCodeFragment) extends Evaluator {

  import CompilingEvaluator._

  override def evaluate(context: EvaluationContextImpl): Value = {
    val process = context.getDebugProcess
    val project = context.getProject

    val autoLoadContext = context.withAutoLoadClasses(true)
    val classLoader = ClassLoadingUtils.getClassLoader(autoLoadContext, process)
    autoLoadContext.setClassLoader(classLoader)

    val (objectName, text) = inReadAction {
      val originalFile = psiContext.getContainingFile
      val nonPhysicalCopy = copy(originalFile, physical = false)
      val range = psiContext.getTextRange
      val psiContextCopy = findElement(nonPhysicalCopy, range, psiContext.getClass)

      var (prevParent, parent) = findAnchorAndParent(psiContextCopy)

      val needBraces = parent match {
        case _: ScBlock | _: ScTemplateBody => false
        case _ => true
      }

      val tabSize = CodeStyle.getIndentOptions(fragment).TAB_SIZE
      val indentLevel = IndentUtil.calcRegionIndent(prevParent, tabSize) + needBraces.fold(tabSize, 0)

      val anchor =
        if (needBraces) {
          val newBlock = ScalaPsiElementFactory.createExpressionWithContextFromText(
            s"""{
               |${indent(indentLevel)(prevParent.getText)}
               |${indent(indentLevel - tabSize)("")}}""".stripMargin,
            prevParent.getContext,
            prevParent
          )
          parent = prevParent.replace(newBlock)
          parent match {
            case bl: ScBlock =>
              bl.statements.head
            case _ => throw EvaluationException("Could not compile Scala code in this context")
          }
        } else prevParent

      val fragmentExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(fragment.getText, anchor.getContext, anchor)
      val fragmentContext = parent.addBefore(fragmentExpr, anchor)
      val newLine = ScalaPsiElementFactory.createNewLine(s"${System.lineSeparator()}${indent(indentLevel)("")}")(fragment.getProject)
      parent.addBefore(newLine, anchor)

      val count = CompilingEvaluator.counter.incrementAndGet()

      s"GeneratedObject$count$$" ->
        s"""object GeneratedObject$count {
           |  def method() = {
           |${indent(4)(fragment.getText)}
           |  }
           |}
         """.stripMargin
    }

    val helper = EvaluatorCompileHelper.implementations.headOption.getOrElse {
      ScalaEvaluatorCompileHelper.instance(project)
    }

    val module = inReadAction(ModuleUtilCore.findModuleForPsiElement(psiContext.getContainingFile))
    val compiled = helper.compile(text, module).filter(_._1.getName.endsWith(".class"))

    compiled.foreach { case (file, name) =>
      val bytes = Files.readAllBytes(file.toPath)
      ClassLoadingUtils.defineClass(name, bytes, autoLoadContext, process, classLoader)
      process.findClass(autoLoadContext, name, classLoader)
    }

    val moduleEvaluator = ScalaFieldEvaluator(new ScalaTypeEvaluator(JVMNameUtil.getJVMRawText(objectName)), "MODULE$")
    val methodEvaluator = ScalaMethodEvaluator(moduleEvaluator, "method", null, Seq.empty)
    methodEvaluator.evaluate(autoLoadContext).asInstanceOf[Value]
  }
}

private object CompilingEvaluator {
  private val counter: AtomicInteger = new AtomicInteger(0)

  private def indent(size: Int)(text: String): String =
    ("" +: text.split(System.lineSeparator())).mkString(" " * size)

  @tailrec
  private def findAnchorAndParent(elem: PsiElement): (ScBlockStatement, PsiElement) = elem match {
    case (stmt: ScBlockStatement) childOf (b: ScBlock) => (stmt, b)
    case (stmt: ScBlockStatement) childOf (funDef: ScFunctionDefinition) if funDef.body.contains(stmt) => (stmt, funDef)
    case (stmt: ScBlockStatement) childOf (nonExpr: PsiElement) => (stmt, nonExpr)
    case _ =>
      elem.parentOfType(classOf[ScBlockStatement]) match {
        case Some(blockStatement) => findAnchorAndParent(blockStatement)
        case _ => throw EvaluationException("Could not compile Scala code in this context")
      }
  }

  private def copy(file: PsiFile, physical: Boolean): PsiFile = {
    val fileFactory = PsiFileFactory.getInstance(file.getProject)
    fileFactory.createFileFromText(file.name, file.getLanguage, file.charSequence, physical, true)
  }

  private def findElement[T <: PsiElement](file: PsiFile, range: TextRange, elementClass: Class[T]): T =
    findElementInRange(file, range.getStartOffset, range.getEndOffset, elementClass, file.getLanguage)
}
