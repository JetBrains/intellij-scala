package org.jetbrains.plugins.scala.debugger.evaluation.evaluator
package compiling

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightUtilCore.findElementInRange
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile, PsiFileFactory}
import org.jetbrains.plugins.scala.debugger.DebuggerBundle
import org.jetbrains.plugins.scala.debugger.evaluation.{EvaluationException, EvaluatorCompileHelper, ScalaEvaluatorCompileHelper}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockStatement, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionWithContextFromText, createNewLine, createTemplateDefinitionFromText}
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.{AnonymousFunction, IndentUtil}

import scala.annotation.tailrec

private[evaluator] case class GeneratedClass(syntheticFile: PsiFile, newContext: PsiElement, generatedClassName: String) {
  import GeneratedClass._

  private val module: Module = inReadAction {
    val originalFile = syntheticFile.getUserData(AnonymousFunction.originalFileKey)
    Option(originalFile).map(findModuleForPsiElement).orNull
  }

  val callText = s"new $generatedClassName().$generatedMethodName()"

  def compiledClasses: Seq[OutputFileObject] = compileGeneratedClass(syntheticFile.getText)

  private def compileGeneratedClass(fileText: String): Seq[OutputFileObject] = {
    if (module == null) throw EvaluationException(DebuggerBundle.message("module.for.compilation.is.not.found"))

    val helper = EvaluatorCompileHelper.implementations.headOption.getOrElse {
      ScalaEvaluatorCompileHelper.instance(module.getProject)
    }
    val compiled = helper.compile(fileText, module)
    compiled.collect {
      case (f, name) if name.contains(generatedClassName) => new OutputFileObject(f, name)
    }.toSeq
  }
}

private[evaluator] object GeneratedClass {
  var counter = 0
  val generatedMethodName: String = "invoke"

  def apply(fragment: ScalaCodeFragment, context: PsiElement): GeneratedClass = {
    counter += 1

    val generatedClassName = "GeneratedEvaluatorClass$" + counter
    val file = context.getContainingFile

    //create and modify non-physical copy first to avoid write action
    val nonPhysicalCopy = copy(file, physical = false)
    val range = context.getTextRange
    val contextCopy = findElement(nonPhysicalCopy, range, context.getClass)

    if (contextCopy == null) throw EvaluationException(DebuggerBundle.message("could.not.evaluate.due.to.a.change.in.a.source.file"))

    val (_, constructor) = addLocalClassAndConstructor(contextCopy, fragment, generatedClassName)
    val newContextRange = constructor.getTextRange

    //create physical file to work with source positions
    val physicalCopy = copy(nonPhysicalCopy, physical = true)
    val newContext = findElement(physicalCopy, newContextRange, classOf[ScNewTemplateDefinition])

    physicalCopy.putUserData(ScalaCompilingEvaluator.classNameKey, generatedClassName)
    physicalCopy.putUserData(AnonymousFunction.originalFileKey, file)

    GeneratedClass(physicalCopy, newContext, generatedClassName)
  }

  private def copy(file: PsiFile, physical: Boolean): PsiFile = {
    val fileFactory = PsiFileFactory.getInstance(file.getProject)

    fileFactory.createFileFromText(file.name, file.getLanguage, file.charSequence, physical, true)
  }

  private def findElement[T <: PsiElement](file: PsiFile, range: TextRange, elementClass: Class[T]): T =
    findElementInRange(file, range.getStartOffset, range.getEndOffset, elementClass, file.getLanguage)

  private def addLocalClassAndConstructor(contextCopy: PsiElement,
                                          fragment: ScalaCodeFragment,
                                          generatedClassName: String): (ScClass, ScNewTemplateDefinition) = {
    @tailrec
    def findAnchorAndParent(elem: PsiElement): (ScBlockStatement, PsiElement) = elem match {
      case (stmt: ScBlockStatement) childOf (b: ScBlock) => (stmt, b)
      case (stmt: ScBlockStatement) childOf (funDef: ScFunctionDefinition) if funDef.body.contains(stmt) => (stmt, funDef)
      case (stmt: ScBlockStatement) childOf (nonExpr: PsiElement) => (stmt, nonExpr)
      case _ =>
        elem.parentOfType(classOf[ScBlockStatement]) match {
          case Some(blockStatement) => findAnchorAndParent(blockStatement)
          case _ => throw EvaluationException(DebuggerBundle.message("could.not.compile.local.class.in.this.context"))
        }
    }

    var (prevParent, parent) = findAnchorAndParent(contextCopy)

    val needBraces = parent match {
      case _: ScBlock | _: ScTemplateBody => false
      case _ => true
    }

    val tabSize = CodeStyle.getIndentOptions(fragment).TAB_SIZE
    val indentLevel = IndentUtil.calcRegionIndent(prevParent, tabSize) + (if (needBraces) tabSize else 0)
    val indent = indentStr(indentLevel)

    val anchor =
      if (needBraces) {
        val newBlock = createExpressionWithContextFromText(
          s"{\n$indent${prevParent.getText}\n${indentStr(indentLevel - tabSize)}}",
          prevParent.getContext,
          prevParent
        )
        parent = prevParent.replace(newBlock)
        parent match {
          case bl: ScBlock =>
            bl.statements.head
          case _ => throw EvaluationException(DebuggerBundle.message("could.not.compile.local.class.in.this.context"))
        }
      }
      else prevParent

    val constructorInvocation =
      createExpressionWithContextFromText(s"new $generatedClassName()", anchor.getContext, anchor)

    implicit val ctx: ProjectContext = fragment.getProject

    val classText = localClassText(fragment, generatedClassName, indentLevel)
    val classToInsert = createTemplateDefinitionFromText(classText, anchor.getContext, anchor).asInstanceOf[ScClass]

    def createNewLineWithIndent() = createNewLine(s"\n$indent")

    val insertedClass = parent.addBefore(classToInsert, anchor)
    parent.addBefore(createNewLineWithIndent(), anchor)

    //add constructor to synthetic file to avoid compiler optimizations
    val insertedConstructor = parent.addBefore(constructorInvocation, anchor)
    parent.addBefore(createNewLineWithIndent(), anchor)

    (insertedClass.asInstanceOf[ScClass], insertedConstructor.asInstanceOf[ScNewTemplateDefinition])
  }

  @inline private def indentStr(spaces: Int) = " " * spaces

  private def indentText(text: String, spaces: Int): String =
    indentStr(spaces) + indentLineBreaks(text, spaces)

  private def indentLineBreaks(text: String, spaces: Int): String =
    text.replace("\n", "\n" + indentStr(spaces))

  private def localClassText(fragment: ScalaCodeFragment, generatedClassName: String, baseIndent: Int = 0): String = {
    val fragmentImports = fragment.importsToString().split(",").filter(_.nonEmpty).map("import _root_." + _)
    val importsText = fragmentImports.mkString("\n")
    val bodyText = Seq(importsText, fragment.getText).filterNot(_.isEmpty).mkString("\n")

    //todo type parameters?
    val generatedClassText =
      s"""|class $generatedClassName {
          |  def $generatedMethodName() = {
          |    ${indentLineBreaks(bodyText, 4)}
          |  }
          |}""".stripMargin

    if (baseIndent > 0) indentText(generatedClassText, baseIndent)
    else generatedClassText
  }
}
