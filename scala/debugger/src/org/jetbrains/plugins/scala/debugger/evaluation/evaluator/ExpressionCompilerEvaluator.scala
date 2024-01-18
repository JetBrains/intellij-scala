package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ExpressionEvaluator, Modifier, UnBoxingEvaluator}
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContext, EvaluationContextImpl}
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.psi.PsiElement
import com.sun.jdi.{ArrayType, ClassLoaderReference, ClassType, ObjectReference, Value}
import org.jetbrains.jps.incremental.scala.DummyClient
import org.jetbrains.jps.incremental.scala.remote.CommandIds
import org.jetbrains.plugins.scala.compiler.data.ExpressionEvaluationArguments
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, RemoteServerRunner}
import org.jetbrains.plugins.scala.debugger.ScalaPositionManager
import org.jetbrains.plugins.scala.debugger.evaluation.EvaluationException
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

private[evaluation] final class ExpressionCompilerEvaluator(codeFragment: PsiElement, position: SourcePosition) extends ExpressionEvaluator {
  override def getModifier: Modifier = null

  override def evaluate(context: EvaluationContext): Value = withCompileServer(context.getProject) {
    val module = inReadAction {
      ModuleUtilCore.findModuleForPsiElement(codeFragment)
    }

    val outDir = {
      val compilerManager = CompilerManager.getInstance(context.getProject)
      createOutputDirectory(compilerManager.getJavacCompilerWorkingDir.toPath)
    }

    try {
      def stripJarPathSuffix(path: String): String =
        path.stripSuffix("!").stripSuffix("!/")

      val enumerator = OrderEnumerator.orderEntries(module).compileOnly().recursively()
      val classpath =
        module.scalaCompilerClasspath.map(_.toPath) ++ enumerator.getClassesRoots.map(_.getCanonicalPath).map(stripJarPathSuffix).map(Path.of(_))
      val scalacOptions = module.scalaCompilerSettings.getOptionsAsStrings(module.hasScala3)
      val source = Path.of(position.getFile.getVirtualFile.getCanonicalPath)
      val line = position.getLine + 1
      val expression = codeFragment.getText

      val stackFrame = context.getFrameProxy.getStackFrame
      val localVariables = stackFrame.visibleVariables().asScala
      val (localVariableNames, localVariableValues) = {
        val names = localVariables.map(_.name())
        val values = localVariables.map(stackFrame.getValue)
        if (names.contains("$this"))
          names -> values
        else {
          val thisObject = stackFrame.thisObject()
          (names :+ "$this") -> (values :+ thisObject)
        }
      }

      val packageName = inReadAction(ScalaPositionManager.findPackageName(position.getElementAt)).getOrElse("")
      val arguments = ExpressionEvaluationArguments(outDir, classpath, scalacOptions, source, line, expression, localVariableNames.toSet, packageName)

      val process = new RemoteServerRunner().buildProcess(CommandIds.EvaluateExpression, arguments.asStrings, new DummyClient())
      process.run()

      val autoLoadContext = context.asInstanceOf[EvaluationContextImpl].withAutoLoadClasses(true)
      val classLoader = createClassLoader(outDir, autoLoadContext)
      autoLoadContext.setClassLoader(classLoader)
      val prefix = if (packageName == "") "" else s"$packageName."
      val className = s"${prefix}CompiledExpression"
      autoLoadContext.getDebugProcess.findClass(autoLoadContext, className, classLoader)

      val localVariableNamesEvaluator: Evaluator = { ctx =>
        val arrayType = ctx.getDebugProcess.findClass(ctx, "java.lang.String[]", ctx.getClassLoader).asInstanceOf[ArrayType]
        val array = DebuggerUtilsEx.mirrorOfArray(arrayType, localVariableNames.length, ctx)
        array.setValues(localVariableNames.map(DebuggerUtilsEx.mirrorOfString(_, ctx.getDebugProcess.getVirtualMachineProxy, ctx)).asJava)
        array
      }

      val localVariableValuesEvaluator: Evaluator = { ctx =>
        val arrayType = ctx.getDebugProcess.findClass(ctx, "java.lang.Object[]", ctx.getClassLoader).asInstanceOf[ArrayType]
        val array = DebuggerUtilsEx.mirrorOfArray(arrayType, localVariableValues.length, ctx)
        array.setValues(localVariableValues.map(ScalaBoxingEvaluator.box(_, ctx).asInstanceOf[Value]).asJava)
        array
      }

      val instance = ScalaMethodEvaluator(new ScalaTypeEvaluator(JVMNameUtil.getJVMRawText(className)), "<init>", null, Seq(localVariableNamesEvaluator, localVariableValuesEvaluator))
      val method = ScalaMethodEvaluator(instance, "evaluate", null, Seq.empty)
      val unboxed = new UnBoxingEvaluator(method)

      unboxed.evaluate(autoLoadContext).asInstanceOf[Value]
    } catch {
      case NonFatal(t) => throw EvaluationException(t)
    } finally {
      Files.walkFileTree(outDir, new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
          if (exc ne null) {
            throw exc
          }
          if (outDir != dir) {
            Files.delete(dir)
          }
          FileVisitResult.CONTINUE
        }
      })
    }
  }

  private def withCompileServer[A](project: Project)(thunk: => A): A = {
    if (!CompileServerLauncher.ensureServerRunning(project))
      throw new EvaluateException("Could not start compile server")

    try thunk
    finally {
      if (!ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED)
        CompileServerLauncher.stopServerAndWaitFor(Duration.Zero)
    }
  }

  private def createOutputDirectory(workingDir: Path): Path = {
    val path = workingDir.resolve("scala-debugger").resolve("out")
    val dir = path.toFile
    if (!dir.exists()) {
      dir.mkdirs()
    }
    path
  }

  private def createClassLoader(outDir: Path, context: EvaluationContextImpl): ClassLoaderReference = {
    val process = context.getDebugProcess
    val thread = context.getFrameProxy.getStackFrame.thread()
    val classLoader = context.getClassLoader

    val pathURL = DebuggerUtilsEx.mirrorOfString(outDir.toUri.toURL.toString, process.getVirtualMachineProxy, context)
    val urlType = process.findClass(context, "java.net.URL", classLoader).asInstanceOf[ClassType]
    val urlConstructor = urlType.concreteMethodByName("<init>", "(Ljava/lang/String;)V")
    val url = urlType.newInstance(thread, urlConstructor, List(pathURL).asJava, ObjectReference.INVOKE_SINGLE_THREADED)
    val urlArrayType = process.findClass(context, "java.net.URL[]", classLoader).asInstanceOf[ArrayType]
    val array = DebuggerUtilsEx.mirrorOfArray(urlArrayType, 1, context)
    array.setValue(0, url)
    val urlClassLoaderType = process.findClass(context, "java.net.URLClassLoader", classLoader).asInstanceOf[ClassType]
    val urlClassLoaderConstructor = urlClassLoaderType.concreteMethodByName("<init>", "([Ljava/net/URL;Ljava/lang/ClassLoader;)V")
    urlClassLoaderType.newInstance(thread, urlClassLoaderConstructor, List(array, classLoader).asJava, ObjectReference.INVOKE_SINGLE_THREADED).asInstanceOf[ClassLoaderReference]
  }
}
