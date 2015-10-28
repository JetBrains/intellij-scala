package org.jetbrains.plugins.scala
package debugger.evaluation.evaluator

import java.io.File
import java.net.URI
import java.util

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.debugger.engine._
import com.intellij.debugger.engine.evaluation._
import com.intellij.debugger.engine.evaluation.expression.{ExpressionEvaluator, Modifier}
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.{DebuggerInvocationUtil, EvaluatingComputable, SourcePosition}
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.{PsiElement, PsiFileFactory}
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.evaluation._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockStatement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.annotation.tailrec
import scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 2014-10-09
 */

class ScalaCompilingEvaluator(psiContext: PsiElement, fragment: ScalaCodeFragment)
        extends ExpressionEvaluator {

  import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.ScalaCompilingEvaluator._

  private val project = inReadAction(psiContext.getProject)
  private val generatedClass = GeneratedClass(fragment, psiContext)
  private var classLoader: ClassLoaderReference = null

  override def getValue: Value = null

  override def getModifier: Modifier = null

  override def evaluate(evaluationContext: EvaluationContext): Value = {
    val process: DebugProcess = evaluationContext.getDebugProcess

    try {
      if (classLoader == null || classLoader.isCollected) classLoader = getClassLoader(evaluationContext)
    }
    catch {
      case e: Exception =>
        throw new EvaluateException("Error creating evaluation class loader:\n " + e, e)
    }

    try {
      defineClasses(generatedClass.compiledClasses, evaluationContext, process, classLoader)
    }
    catch {
      case e: Exception =>
        throw new EvaluateException("Error during classes definition:\n " + e, e)
    }

    try {
      val evaluator = callEvaluator(evaluationContext)
      evaluationContext.asInstanceOf[EvaluationContextImpl].setClassLoader(classLoader)
      evaluator.evaluate(evaluationContext)
    }
    catch {
      case e: Exception =>
        throw new EvaluateException("Error during generated code invocation:\n " + e, e)
    }
  }

  private def callEvaluator(evaluationContext: EvaluationContext): ExpressionEvaluator = {
    DebuggerInvocationUtil.commitAndRunReadAction(project, new EvaluatingComputable[ExpressionEvaluator] {
      override def compute(): ExpressionEvaluator = {
        val callCode = new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, generatedClass.callText)
        val codeFragment = new ScalaCodeFragmentFactory().createCodeFragment(callCode, generatedClass.getAnchor, project)
        ScalaEvaluatorBuilder.build(codeFragment, SourcePosition.createFromElement(generatedClass.getAnchor))
      }
    })
  }

  private def defineClasses(classes: Seq[OutputFileObject], context: EvaluationContext,
                            process: DebugProcess, classLoader: ClassLoaderReference): Unit = {
    if (classes.isEmpty) throw EvaluationException("Could not compile generated class")
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

  val classNameKey = Key.create[String]("generated.class.name")

  private def keep(reference: ObjectReference, context: EvaluationContext) {
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
      case (b, i) => reference.setValue(i, process.getVirtualMachineProxy.asInstanceOf[VirtualMachineProxyImpl].mirrorOf(bytes(i)))
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

private class GeneratedClass(fragment: ScalaCodeFragment, context: PsiElement, id: Int) {

  private val project: Project = context.getProject

  val generatedClassName = "GeneratedEvaluatorClass$" + id
  val generatedMethodName = "invoke"
  val callText = s"new $generatedClassName().$generatedMethodName()"
  var compiledClasses: Seq[OutputFileObject] = null

  private var anchorRange: TextRange = null
  
  private var anchor: PsiElement = null
  def getAnchor = anchor

  init()

  private def init(): Unit = {
    val file = context.getContainingFile

    //create and modify non-physical copy first to avoid write action
    val textWithLocalClass = createFileTextWithLocalClass(context)
    //create physical file to work with source positions
    val copy = PsiFileFactory.getInstance(project).createFileFromText(file.getName, file.getFileType, textWithLocalClass, file.getModificationStamp, true)
    copy.putUserData(ScalaCompilingEvaluator.classNameKey, generatedClassName)

    anchor = CodeInsightUtilCore.findElementInRange(copy, anchorRange.getStartOffset, anchorRange.getEndOffset, classOf[ScBlockStatement], file.getLanguage)
    compileGeneratedClass(copy.getText)
  }
  
  private def compileGeneratedClass(fileText: String): Unit = {
    val module = inReadAction(ModuleUtilCore.findModuleForPsiElement(context))

    if (module == null) throw EvaluationException("Could not evaluate due to a change in a source file")

    val helper = EvaluatorCompileHelper.EP_NAME.getExtensions.headOption.getOrElse {
      ScalaEvaluatorCompileHelper.instance(project)
    }
    val compiled = helper.compile(fileText, module)
    compiledClasses = compiled.collect {
      case (f, name) if name.contains(generatedClassName) => new OutputFileObject(f, name)
    }
  }

  private def createFileTextWithLocalClass(context: PsiElement): String = {
    val file = context.getContainingFile
    val copy = PsiFileFactory.getInstance(project).createFileFromText(file.getName, file.getFileType, file.getText, file.getModificationStamp, false)
    val range = context.getTextRange
    val copyContext: PsiElement = CodeInsightUtilCore.findElementInRange(copy, range.getStartOffset, range.getEndOffset, context.getClass, file.getLanguage)

    if (copyContext == null) throw EvaluationException("Could not evaluate due to a change in a source file")

    val clazz = localClass(fragment, copyContext)
    addLocalClass(copyContext, clazz)
    copy.getText
  }

  private def addLocalClass(context: PsiElement, scClass: ScClass): Unit = {
    @tailrec
    def findAnchorAndParent(elem: PsiElement): (ScBlockStatement, PsiElement) = elem match {
      case (stmt: ScBlockStatement) childOf (b: ScBlock) => (stmt, b)
      case (stmt: ScBlockStatement) childOf (funDef: ScFunctionDefinition) if funDef.body.contains(stmt) => (stmt, funDef)
      case (elem: PsiElement) childOf (other: ScBlockStatement) => findAnchorAndParent(other)
      case (stmt: ScBlockStatement) childOf (nonExpr: PsiElement) => (stmt, nonExpr)
      case _ => throw EvaluationException("Could not compile local class in this context")
    }

    var (prevParent, parent) = findAnchorAndParent(context)

    val needBraces = parent match {
      case _: ScBlock | _: ScTemplateBody => false
      case _ => true
    }

    val anchor =
      if (needBraces) {
        val newBlock = ScalaPsiElementFactory.createExpressionWithContextFromText(s"{\n${prevParent.getText}\n}", prevParent.getContext, prevParent)
        parent = prevParent.replace(newBlock)
        parent match {
          case bl: ScBlock =>
            bl.statements.head
          case _ => throw EvaluationException("Could not compile local class in this context")
        }
      }
      else prevParent

    val newInstance = ScalaPsiElementFactory.createExpressionWithContextFromText(s"new $generatedClassName()", anchor.getContext, anchor)

    parent.addBefore(scClass, anchor)
    parent.addBefore(ScalaPsiElementFactory.createNewLine(context.getManager), anchor)
    parent.addBefore(newInstance, anchor)
    parent.addBefore(ScalaPsiElementFactory.createNewLine(context.getManager), anchor)
    anchorRange = anchor.getTextRange
  }

  private def localClass(fragment: ScalaCodeFragment, context: PsiElement) = {
    val fragmentImports = fragment.importsToString().split(",").filter(!_.isEmpty).map("import _root_." + _)
    val importsText = fragmentImports.mkString("\n")
    //todo type parameters?
    val text =
     s"""|class $generatedClassName {
         |  def $generatedMethodName() = {
         |    $importsText
         |    
         |    ${fragment.getText}
         |  }
         |}""".stripMargin
    ScalaPsiElementFactory.createTemplateDefinitionFromText(text, context.getContext, context).asInstanceOf[ScClass]
  }
}

private object GeneratedClass {
  var counter = 0

  def apply(fragment: ScalaCodeFragment, context: PsiElement) = {
    counter += 1
    new GeneratedClass(fragment, context, counter)
  }
}
