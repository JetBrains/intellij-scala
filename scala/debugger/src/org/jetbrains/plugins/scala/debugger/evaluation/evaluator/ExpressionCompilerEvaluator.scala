package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.expression.{Evaluator, ExpressionEvaluator, IdentityEvaluator, Modifier, UnBoxingEvaluator}
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContext, EvaluationContextImpl}
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.psi.PsiElement
import com.sun.jdi.{ArrayType, ClassLoaderReference, ClassType, ObjectReference, Value}
import org.jetbrains.jps.incremental.scala.remote.CommandIds
import org.jetbrains.jps.incremental.scala.{Client, DummyClient, MessageKind}
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.compiler.data.ExpressionEvaluationArguments
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, CompilerManagerUtil, RemoteServerRunner}
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.ExpressionCompilerEvaluator.filteredScalacOptions
import org.jetbrains.plugins.scala.debugger.evaluation.{EvaluationException, ExpressionCompilerResolverListener}
import org.jetbrains.plugins.scala.debugger.{DebuggerBundle, ScalaPositionManager}
import org.jetbrains.plugins.scala.extensions.{PathExt, inReadAction}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
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
    val moduleForPsiElement = inReadAction {
      ModuleUtilCore.findModuleForPsiElement(codeFragment)
    }

    if (moduleForPsiElement eq null) {
      val codeFragmentText = inReadAction(codeFragment.getText)
      throw EvaluationException(DebuggerBundle.message("could.not.find.module.for.code.fragment", codeFragmentText))
    }

    val module = moduleForPsiElement.findRepresentativeModuleForSharedSourceModuleOrSelf

    val outDir = createOutputDirectory(CompilerManagerUtil.javacCompilerWorkingDir(context.getProject))

    val scalaVersion = module.scalaMinorVersion match {
      case Some(version) => version
      case None =>
        throw EvaluationException(DebuggerBundle.message("could.not.determine.scala.version", module.getName))
    }

    val expressionCompilerType = {
      import ExpressionCompilerResolverListener.{ExpressionCompilers, ScalaExpressionCompilerVersion}
      context.getProject.getUserData(ExpressionCompilers)
        .getOrElse(scalaVersion, throw EvaluationException(DebuggerBundle.message("could.not.resolve.scala.expression.compiler", ScalaExpressionCompilerVersion, scalaVersion.minor)))
    }

    import ExpressionCompilerResolverListener.ExpressionCompilerType
    val expressionCompilerJar = expressionCompilerType match {
      case ExpressionCompilerType.BuiltIn => Seq.empty
      case ExpressionCompilerType.ResolvedJar(path) => Seq(path)
    }
    val useBuiltInExpressionCompiler = expressionCompilerType == ExpressionCompilerType.BuiltIn

    try {
      def stripJarPathSuffix(path: String): String =
        path.stripSuffix("!").stripSuffix("!/")

      val enumerator = OrderEnumerator.orderEntries(module).compileOnly().recursively()
      val classpath =
        module.scalaCompilerClasspath ++
          enumerator.getClassesRoots.map(_.getCanonicalPath).map(stripJarPathSuffix).map(Path.of(_)) ++
          expressionCompilerJar
      val scalacOptions = filteredScalacOptions(ScalaCompilerSettings.forModule(module).getOptionsAsStrings(module.hasScala3))
      val source = Path.of(position.getFile.getVirtualFile.getCanonicalPath)
      val line = position.getLine + 1
      val expression = codeFragment.getText

      val stackFrame = context.getFrameProxy.getStackFrame
      val localVariables = stackFrame.visibleVariables().asScala
      val (localVariableNames, localVariableValues) = {
        val names = localVariables.map(_.name())
        val values = localVariables.map(stackFrame.getValue)
        names -> values
      }
      val thisObject = stackFrame.thisObject()

      val packageName = inReadAction(ScalaPositionManager.findPackageName(position.getElementAt)).getOrElse("")
      val arguments = ExpressionEvaluationArguments(useBuiltInExpressionCompiler, outDir, classpath, scalacOptions, source, line, expression, localVariableNames.toSet, packageName)

      val errors = Seq.newBuilder[NlsString]
      val client = new DummyClient() {
        override def message(msg: Client.ClientMsg): Unit = {
          if (msg.kind == MessageKind.Error) {
            errors += NlsString(msg.text)
          }
        }
      }

      val process = new RemoteServerRunner().buildProcess(CommandIds.EvaluateExpression, arguments.asStrings, client)

      var result: Either[Seq[NlsString], Unit] = Right(())
      process.addTerminationCallback { _ =>
        val foundErrors = errors.result()
        if (foundErrors.nonEmpty) {
          result = Left(foundErrors)
        }
      }
      process.run()

      result match {
        case Left(errors) =>
          val message = DebuggerBundle.message("expression.compilation.failed", errors.mkString(System.lineSeparator()))
          throw EvaluationException(message)
        case Right(()) =>
      }

      val autoLoadContext = context.asInstanceOf[EvaluationContextImpl].withAutoLoadClasses(true)
      val classLoader = createClassLoader(outDir, autoLoadContext)
      autoLoadContext.setClassLoader(classLoader)
      val prefix = if (packageName == "") "" else s"$packageName."
      val className = s"${prefix}CompiledExpression"
      autoLoadContext.getDebugProcess.findClass(autoLoadContext, className, classLoader)

      val localVariableNamesEvaluator: Evaluator = new Evaluator {
        override def evaluate(ctx: EvaluationContextImpl): AnyRef = {
          val arrayType = ctx.getDebugProcess.findClass(ctx, "java.lang.String[]", ctx.getClassLoader).asInstanceOf[ArrayType]
          val array = DebuggerUtilsEx.mirrorOfArray(arrayType, localVariableNames.length, ctx)
          array.setValues(localVariableNames.map(DebuggerUtilsEx.mirrorOfString(_, ctx)).asJava)
          array
        }
      }

      val localVariableValuesEvaluator: Evaluator = new Evaluator {
        override def evaluate(ctx: EvaluationContextImpl): AnyRef = {
          val arrayType = ctx.getDebugProcess.findClass(ctx, "java.lang.Object[]", ctx.getClassLoader).asInstanceOf[ArrayType]
          val array = DebuggerUtilsEx.mirrorOfArray(arrayType, localVariableValues.length, ctx)
          array.setValues(localVariableValues.map(ScalaBoxingEvaluator.box(_, ctx).asInstanceOf[Value]).asJava)
          array
        }
      }

      val thisEvaluator = new IdentityEvaluator(thisObject)
      val instance = ScalaMethodEvaluator(new ScalaTypeEvaluator(JVMNameUtil.getJVMRawText(className)), "<init>", null, Seq(thisEvaluator, localVariableNamesEvaluator, localVariableValuesEvaluator))
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
    if (!path.exists) {
      Files.createDirectories(path)
    }
    path
  }

  private def createClassLoader(outDir: Path, context: EvaluationContextImpl): ClassLoaderReference = {
    val process = context.getDebugProcess
    val thread = context.getFrameProxy.getStackFrame.thread()
    val classLoader = context.getClassLoader

    val pathURL = DebuggerUtilsEx.mirrorOfString(outDir.toUri.toURL.toString, context)
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

private object ExpressionCompilerEvaluator {
  private final val IgnoredScalacOptions: Array[String] = Array("-Werror", "-Xfatal-warnings")

  private def filteredScalacOptions(scalacOptions: Seq[String]): Seq[String] =
    scalacOptions.filterNot(IgnoredScalacOptions.contains)
}
