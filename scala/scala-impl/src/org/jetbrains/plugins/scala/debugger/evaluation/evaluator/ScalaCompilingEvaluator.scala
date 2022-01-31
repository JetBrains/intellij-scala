package org.jetbrains.plugins.scala
package debugger.evaluation.evaluator

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightUtilCore.findElementInRange
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine._
import com.intellij.debugger.engine.evaluation._
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ExpressionEvaluator, Modifier}
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.{PsiElement, PsiFile, PsiFileFactory}
import com.sun.jdi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.debugger.evaluation._
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.GeneratedClass.generatedMethodName
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockStatement, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.IndentUtil

import java.io.File
import java.net.URI
import java.util
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

/**
 * Nikolay.Tropin
 * 2014-10-09
 */

class ScalaCompilingExpressionEvaluator(evaluator: ScalaCompilingEvaluator) extends ExpressionEvaluator {
  override def evaluate(context: EvaluationContext): Value = evaluator.evaluate(context.asInstanceOf[EvaluationContextImpl])

  override def getValue: Value = null

  override def getModifier: Modifier = evaluator.getModifier
}

class ScalaCompilingEvaluator(psiContext: PsiElement, fragment: ScalaCodeFragment)
        extends Evaluator {

  import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.ScalaCompilingEvaluator._

  private val project = inReadAction(fragment.getProject)
  private val generatedClass = GeneratedClass(fragment, psiContext)
  private var classLoader: ClassLoaderReference = null

  override def getModifier: Modifier = null

  override def evaluate(context: EvaluationContextImpl): Value = {
    val process: DebugProcess = context.getDebugProcess

    try {
      if (classLoader == null || classLoader.isCollected) classLoader = getClassLoader(context)
    }
    catch {
      case e: Exception =>
        throw EvaluationException(ScalaBundle.message("error.creating.evaluation.class.loader", e), e)
    }

    try {
      defineClasses(generatedClass.compiledClasses, context, process, classLoader)
    }
    catch {
      case e: Exception =>
        throw EvaluationException(ScalaBundle.message("error.during.classes.definition", e), e)
    }

    try {
      val evaluator = callEvaluator(context)
      context.setClassLoader(classLoader)
      evaluator.evaluate(context)
    }
    catch {
      case e: Exception =>
        throw EvaluationException(ScalaBundle.message("error.during.generated.code.invocation", e), e)
    }
  }

  private def callEvaluator(evaluationContext: EvaluationContext): ExpressionEvaluator =
    inReadAction {
      val callCode = new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, generatedClass.callText)
      val codeFragment = new ScalaCodeFragmentFactory().createCodeFragment(callCode, generatedClass.newContext, project)
      ScalaEvaluatorBuilder.build(codeFragment, SourcePosition.createFromElement(generatedClass.newContext))
    }

  private def defineClasses(classes: Seq[OutputFileObject], context: EvaluationContext,
                            process: DebugProcess, classLoader: ClassLoaderReference): Unit = {
    if (classes.isEmpty) throw EvaluationException(ScalaBundle.message("could.not.compile.generated.class"))
    val proxy: VirtualMachineProxyImpl = process.getVirtualMachineProxy.asInstanceOf[VirtualMachineProxyImpl]
    def alreadyDefined(clsName: String) = {
      proxy.classesByName(clsName).asScala.exists(refType => refType.isPrepared)
    }

    val classLoaderType = classLoader.referenceType.asInstanceOf[ClassType]
    val defineMethod: Method = classLoaderType.concreteMethodByName("defineClass", "(Ljava/lang/String;[BII)Ljava/lang/Class;")
    for (cls <- classes if !alreadyDefined(cls.origName)) {
      val bytes: Array[Byte] = cls.toByteArray
      val args: util.ArrayList[Value] = new util.ArrayList[Value]
      val name: StringReference = proxy.mirrorOf(cls.origName)
      keep(name, context)
      args.add(name)
      args.add(mirrorOf(bytes, context, process))
      args.add(proxy.mirrorOf(0))
      args.add(proxy.mirrorOf(bytes.length))
      process.invokeMethod(context, classLoader, defineMethod, args)
      process.findClass(context, cls.origName, classLoader)
    }
  }

  private def getClassLoader(context: EvaluationContext): ClassLoaderReference = {
    val process = context.getDebugProcess
    val loaderClass = process.findClass(context, "java.net.URLClassLoader", context.getClassLoader).asInstanceOf[ClassType]
    val ctorMethod = loaderClass.concreteMethodByName("<init>", "([Ljava/net/URL;Ljava/lang/ClassLoader;)V")
    val threadReference: ThreadReference = context.getSuspendContext.getThread.getThreadReference
    val args = util.Arrays.asList(createURLArray(context), context.getClassLoader)
    val reference = loaderClass.newInstance(threadReference, ctorMethod, args, ClassType.INVOKE_SINGLE_THREADED)
            .asInstanceOf[ClassLoaderReference]
    keep(reference, context)
    reference
  }

}

object ScalaCompilingEvaluator {

  val classNameKey: Key[String] = Key.create[String]("generated.class.name")
  val originalFileKey: Key[PsiFile] = Key.create[PsiFile]("compiling.evaluator.original.file")

  private def keep(reference: ObjectReference, context: EvaluationContext): Unit = {
    context.getSuspendContext.asInstanceOf[SuspendContextImpl].keep(reference)
  }

  private def createURLArray(context: EvaluationContext): ArrayReference = {
    val process = context.getDebugProcess
    val arrayType = process.findClass(context, "java.net.URL[]", context.getClassLoader).asInstanceOf[ArrayType]
    val arrayRef = arrayType.newInstance(1)
    keep(arrayRef, context)
    val classType = process.findClass(context, "java.net.URL", context.getClassLoader).asInstanceOf[ClassType]
    val proxy: VirtualMachineProxyImpl = process.getVirtualMachineProxy.asInstanceOf[VirtualMachineProxyImpl]
    val threadReference: ThreadReference = context.getSuspendContext.getThread.getThreadReference
    val url = proxy.mirrorOf("file:a")
    keep(url, context)
    val ctorMethod = classType.concreteMethodByName("<init>", "(Ljava/lang/String;)V")
    val reference = classType.newInstance(threadReference, ctorMethod, util.Arrays.asList(url), ClassType.INVOKE_SINGLE_THREADED)
    keep(reference, context)
    arrayRef.setValues(util.Arrays.asList(reference))
    arrayRef
  }

  private def mirrorOf(bytes: Array[Byte], context: EvaluationContext, process: DebugProcess): ArrayReference = {
    val arrayClass: ArrayType = process.findClass(context, "byte[]", context.getClassLoader).asInstanceOf[ArrayType]
    val reference: ArrayReference = process.newInstance(arrayClass, bytes.length)
    keep(reference, context)
    bytes.zipWithIndex.foreach {
      case (_, i) => reference.setValue(i, process.getVirtualMachineProxy.asInstanceOf[VirtualMachineProxyImpl].mirrorOf(bytes(i)))
      case _ =>
    }
    reference
  }
}

class OutputFileObject(file: File, val origName: String) {
  private def getUri(name: String): URI = {
    URI.create("memo:///" + name.replace('.', '/') + ".class")
  }

  def getName: String = getUri(origName).getPath
  def toByteArray: Array[Byte] = FileUtil.loadFileBytes(file)
}

private[evaluator] case class GeneratedClass(syntheticFile: PsiFile, newContext: PsiElement, generatedClassName: String) {

  private val module: Module = inReadAction {
    val originalFile = syntheticFile.getUserData(ScalaCompilingEvaluator.originalFileKey)
    Option(originalFile).map(findModuleForPsiElement).orNull
  }

  val callText = s"new $generatedClassName().$generatedMethodName()"

  def compiledClasses: Seq[OutputFileObject] = compileGeneratedClass(syntheticFile.getText)

  private def compileGeneratedClass(fileText: String): Seq[OutputFileObject] = {
    if (module == null) throw EvaluationException(ScalaBundle.message("module.for.compilation.is.not.found"))

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

    if (contextCopy == null) throw EvaluationException(ScalaBundle.message("could.not.evaluate.due.to.a.change.in.a.source.file"))

    val (scClass, constructor) = addLocalClassAndConstructor(contextCopy, fragment, generatedClassName)
    val newContextRange = constructor.getTextRange

    //create physical file to work with source positions
    val physicalCopy = copy(nonPhysicalCopy, physical = true)
    val newContext = findElement(physicalCopy, newContextRange, classOf[ScNewTemplateDefinition])

    physicalCopy.putUserData(ScalaCompilingEvaluator.classNameKey, generatedClassName)
    physicalCopy.putUserData(ScalaCompilingEvaluator.originalFileKey, file)

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
          case _ => throw EvaluationException(ScalaBundle.message("could.not.compile.local.class.in.this.context"))
        }
    }

    var (prevParent, parent) = findAnchorAndParent(contextCopy)

    val needBraces = parent match {
      case _: ScBlock | _: ScTemplateBody => false
      case _ => true
    }

    val tabSize = CodeStyle.getIndentOptions(fragment).TAB_SIZE
    val indentLevel = IndentUtil.calcRegionIndent(prevParent, tabSize) + needBraces.fold(tabSize, 0)
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
          case _ => throw EvaluationException(ScalaBundle.message("could.not.compile.local.class.in.this.context"))
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