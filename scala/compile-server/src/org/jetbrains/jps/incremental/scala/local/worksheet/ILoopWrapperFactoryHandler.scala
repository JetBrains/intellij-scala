package org.jetbrains.jps.incremental.scala.local.worksheet

import org.jetbrains.jps.incremental.scala.local.worksheet.repl_interface.ILoopWrapper
import org.jetbrains.jps.incremental.scala.local.worksheet.util.IOUtils
import org.jetbrains.jps.incremental.scala.local.{CompilerFactoryImpl, NullLogger}
import org.jetbrains.jps.incremental.scala.{Client, MessageKind, compilerVersion}
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs
import org.jetbrains.plugins.scala.compiler.data.{CompilerJars, SbtData}
import org.jetbrains.plugins.scala.project.Version
import sbt.internal.inc.{AnalyzingCompiler, RawCompiler}
import sbt.util.{Level, Logger}
import xsbti.compile.{ClasspathOptionsUtil, ScalaInstance}

import java.io.{File, PrintStream}
import java.lang.reflect.InvocationTargetException
import java.net.{URLClassLoader, URLDecoder}
import java.nio.file.Path

class ILoopWrapperFactoryHandler {
  import ILoopWrapperFactoryHandler._

  private var cachedReplFactory: Option[CachedReplFactory] = None

  def loadReplWrapperAndRun(
    args: WorksheetArgs.RunRepl,
    replContext: ReplContext,
    out: PrintStream, // the consumed output is delegated to client then encoded and delegated to Nailgun out and sent to the
    client: Client
  ): Unit = try {
    val compilerJars = replContext.compilerJars
    val compilerVersionFromProperties  = compilerVersion(compilerJars.compilerJar)
    val scalaVersion = compilerVersionFromProperties.fold(FallBackScalaVersion)(ScalaVersion.apply)
    val replWrapper = getOrCompileReplWrapper(replContext, scalaVersion, client)

    if (args.dropCachedReplInstance) {
      cachedReplFactory.foreach(_.replFactory.clearSession(args.sessionId))
    }

    // TODO: improve caching, for now we can have only 1 instance with 1 version of scala
    val cachedFactory = cachedReplFactory match {
      case Some(cached@CachedReplFactory(_, _, oldVersion)) if oldVersion == scalaVersion =>
        client.internalDebug("using cached cachedReplFactory")
        cached
      case _ =>
        client.internalDebug("creating new cachedReplFactory")
        val loader = createClassLoader(compilerJars)
        val iLoopWrapper = new ILoopWrapperFactory
        cachedReplFactory.foreach(_.replFactory.clearCaches())
        val cached = CachedReplFactory(loader, iLoopWrapper, scalaVersion)
        cached
    }

    cachedReplFactory = Some(cachedFactory)

    client.progress("Running REPL...")
    IOUtils.patchSystemOut(out)
    val factory = cachedFactory.replFactory
    factory.loadReplWrapperAndRun(args, replContext, out, replWrapper, client, cachedFactory.classLoader)
  } catch {
    case e: InvocationTargetException =>
      throw e.getTargetException
  }

  protected def getOrCompileReplWrapper(replContext: ReplContext, scalaVersion: ScalaVersion, client: Client): ReplWrapperCompiled = {
    val ReplContext(sbtData, compilerJars, _, _) = replContext
    val ILoopWrapperDescriptor(wrapperClassName, wrapperVersion) = wrapperClassNameFor(scalaVersion)
    val replLabel = s"repl-wrapper-${scalaVersion.value}-${sbtData.javaClassVersion}-$wrapperVersion-$wrapperClassName.jar"
    val targetFile = new File(sbtData.interfacesHome, replLabel)

    if (!targetFile.exists) {
      val scalaInstance = CompilerFactoryImpl.getOrCreateScalaInstance(compilerJars)
      compileReplLoopFile(scalaInstance, sbtData, wrapperClassName, replLabel, targetFile, client)
    }

    ReplWrapperCompiled(targetFile, wrapperClassName, scalaVersion)
  }

  private val ReplCompilationFailedMessage = "Repl wrapper compilation failed"

  private def compileReplLoopFile(scalaInstance: ScalaInstance,
                                  sbtData: SbtData,
                                  iLoopWrapperClass: String,
                                  replLabel: String,
                                  targetJar: File,
                                  client: Client): Unit = {
    def findContainingJarOrReport(clazz: Class[_]): Option[File] = {
      val res = findContainingJar(clazz)
      if (res.isEmpty)
        client.error(s"$ReplCompilationFailedMessage: jar for class `${clazz.getName}` can't be found")
      res
    }

    // sources containing ILoopWrapper213Impl.scala and ILoopWrapperImpl.scala
    val interfaceJar = sbtData.compilerInterfaceJar
    // compiler-jps.jar
    val containingJar = findContainingJarOrReport(this.getClass).getOrElse(return)
    val replInterfaceJar = findContainingJarOrReport(classOf[ILoopWrapper]).getOrElse(return)
    val replInterfaceSourcesJar = replInterfaceJar // we pack sources in resources in same jar

    client.progress("Compiling REPL runner...")

    val logger = NullLogger //new ClientDelegatingLogger(client)
    sbtData.interfacesHome.mkdirs()

    def filter(file: File): Boolean =
      file.getName.endsWith(s"$iLoopWrapperClass.scala")

    val rawCompiler = new RawCompiler(scalaInstance, ClasspathOptionsUtil.auto(), logger) {
      override def apply(sources: Seq[Path], classpath: Seq[Path], outputDirectory: Path, options: Seq[String]): Unit = {
        val sourcesFiltered = sources.filter(path => filter(path.toFile))
        super.apply(sourcesFiltered, classpath, outputDirectory, options)
      }
    }
    try
      AnalyzingCompiler.compileSources(
        Seq(replInterfaceSourcesJar.toPath),
        targetJar.toPath,
        xsbtiJars = Seq(interfaceJar, containingJar, replInterfaceJar).distinct.map(_.toPath),
        id = replLabel,
        compiler = rawCompiler,
        log = logger
      )
    catch {
      case compilationFailed: sbt.internal.inc.CompileFailed =>
        val indent = "  "
        val message =
          s"""$ReplCompilationFailedMessage: ${compilationFailed.toString}
             |Arguments:
             |${compilationFailed.arguments.map(indent + _.trim).mkString("\n")}
             |Problems:
             |${compilationFailed.problems.map(indent + _.toString).mkString("\n")}""".stripMargin
        client.error(message)
        throw compilationFailed
    }
  }
}

//noinspection TypeAnnotation
object ILoopWrapperFactoryHandler {

  private case class CachedReplFactory(
    classLoader: ClassLoader,
    replFactory: ILoopWrapperFactory,
    scalaVersion: ScalaVersion
  )

  // ATTENTION: when editing ILoopWrapperXXXImpl.scala ensure to increase the version
  private case class ILoopWrapperDescriptor(className: String, version: Int)
  private def Scala2ILoopWrapperVersion = 10
  private def Scala3ILoopWrapperVersion = 16
  // 2.12 works OK for 2.11 as well
  private def ILoopWrapper212Impl    = ILoopWrapperDescriptor("ILoopWrapper212Impl", Scala2ILoopWrapperVersion)
  private def ILoopWrapper212_13Impl = ILoopWrapperDescriptor("ILoopWrapper212_13Impl", Scala2ILoopWrapperVersion)
  private def ILoopWrapper213_0Impl  = ILoopWrapperDescriptor("ILoopWrapper213_0Impl", Scala2ILoopWrapperVersion)
  private def ILoopWrapper213Impl    = ILoopWrapperDescriptor("ILoopWrapper213Impl", Scala2ILoopWrapperVersion)
  private def ILoopWrapper300Impl    = ILoopWrapperDescriptor("ILoopWrapper300Impl", Scala3ILoopWrapperVersion)
  private def ILoopWrapper312Impl    = ILoopWrapperDescriptor("ILoopWrapper312Impl", Scala3ILoopWrapperVersion)

  private def wrapperClassNameFor(version: ScalaVersion): ILoopWrapperDescriptor = {
    val versionStr = version.value.presentation

    val wrapper = if (versionStr.startsWith("2.13.0")) ILoopWrapper213_0Impl
    else if (versionStr.startsWith("2.13")) ILoopWrapper213Impl
    else if (version.isScala3) {
      //TODO: this is basically equivalent to `3.0.0 <= version < 3.1.2,
      //  reuse org.jetbrains.plugins.scala.project.Version
      if (versionStr.startsWith("3.0") || versionStr.startsWith("3.1.0") || versionStr.startsWith("3.1.1"))
        ILoopWrapper300Impl
      else
        ILoopWrapper312Impl
    }
    // note: lexicographic comparison is used, but it should work fine
    else if (version.value >= Version("2.12.13")) ILoopWrapper212_13Impl
    else ILoopWrapper212Impl
    wrapper
  }

  private[worksheet] case class ReplWrapperCompiled(file: File, className: String, version: ScalaVersion)

  private[worksheet] case class ScalaVersion(value: Version) {
    // temp solution while dotty is evolving very fast
    val isScala3: Boolean = value.presentation.startsWith("3.")
  }

  private[worksheet] object ScalaVersion {
    def apply(versionStr: String): ScalaVersion = ScalaVersion(Version(versionStr))
  }

  private val FallBackScalaVersion = ScalaVersion("2.12.0")

  private def findContainingJar(clazz: Class[_]): Option[File] = {
    val resource = clazz.getResource(s"/${clazz.getName.replace('.', '/')}.class")

    if (resource == null) return None

    val url = URLDecoder.decode(resource.toString.stripPrefix("jar:file:"), "UTF-8")
    val idx = url.indexOf(".jar!")
    if (idx == -1) return None

    Some(new File(url.substring(0, idx + 4))).filter(_.exists())
  }

  private def createClassLoader(compilerJars: CompilerJars): URLClassLoader = {
    val jars = compilerJars.allJars
    val replInterfaceLoader = classOf[ILoopWrapper].getClassLoader
    new URLClassLoader(sbt.io.Path.toURLs(jars), replInterfaceLoader)
  }

  // use for debugging
  private class ClientDelegatingLogger(client: Client) extends Logger {
    override def trace(t: => Throwable): Unit = client.trace(t)
    override def success(message: => String): Unit = client.info(s"success: $message")
    override def log(level: Level.Value, message: => String): Unit = client.message(toMessageKind(level), message)

    private def toMessageKind(level: Level.Value): MessageKind = level match {
      case sbt.util.Level.Debug => MessageKind.Info
      case sbt.util.Level.Info  => MessageKind.Info
      case sbt.util.Level.Warn  => MessageKind.Warning
      case Level.Error          => MessageKind.Error
    }
  }

  case class ReplContext(sbtData: SbtData,
                         compilerJars: CompilerJars,
                         classpath: Seq[File],
                         scalacOptions: Seq[String])
}