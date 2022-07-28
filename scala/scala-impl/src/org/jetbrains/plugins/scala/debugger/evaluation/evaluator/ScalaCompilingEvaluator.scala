package org.jetbrains.plugins.scala
package debugger.evaluation.evaluator

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine._
import com.intellij.debugger.engine.evaluation._
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ExpressionEvaluator, Modifier}
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiElement, PsiFile}
import com.sun.jdi._
import org.jetbrains.plugins.scala.debugger.evaluation._
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.compiling._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment

import java.util
import scala.jdk.CollectionConverters._

class ScalaCompilingEvaluator(psiContext: PsiElement, fragment: ScalaCodeFragment)
        extends Evaluator {

  import ScalaCompilingEvaluator._

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
      val evaluator = callEvaluator
      context.setClassLoader(classLoader)
      evaluator.evaluate(context)
    }
    catch {
      case e: Exception =>
        throw EvaluationException(ScalaBundle.message("error.during.generated.code.invocation", e), e)
    }
  }

  private def callEvaluator: ExpressionEvaluator =
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
