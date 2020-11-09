package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.{File, PrintStream}
import java.lang.reflect.InvocationTargetException
import java.net.{URLClassLoader, URLDecoder}
import java.nio.file.Path

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.local.worksheet.util.{IOUtils, IsolatingClassLoader}
import org.jetbrains.jps.incremental.scala.local.{CompilerFactoryImpl, NullLogger}
import org.jetbrains.jps.incremental.scala.{Client, compilerVersion}
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs
import org.jetbrains.plugins.scala.compiler.data.{CompilerJars, SbtData}
import org.jetbrains.plugins.scala.worksheet.reporters._
import sbt.internal.inc.{AnalyzingCompiler, RawCompiler}
import sbt.util.{Level, Logger}
import xsbti.compile.{ClasspathOptionsUtil, ScalaInstance}

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
    val compilerVersionFromProperties  = compilerVersion(compilerJars.compiler)
    val scalaVersion = compilerVersionFromProperties.fold(FallBackScalaVersion)(ScalaVersion)
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
        val loader = createIsolatingClassLoader(compilerJars)
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


  private def compileReplLoopFile(scalaInstance: ScalaInstance,
                                  sbtData: SbtData,
                                  iLoopWrapperClass: String,
                                  replLabel: String,
                                  targetJar: File,
                                  client: Client): Unit = {
    // sources containing ILoopWrapper213Impl.scala and ILoopWrapperImpl.scala
    val sourceJar = {
      val jpsJarsFolder = sbtData.compilerBridges.scala._2_11.getParent
      new File(jpsJarsFolder, "repl-interface-sources.jar")
    }
    val interfaceJar = sbtData.compilerInterfaceJar
    // compiler-jps.jar
    val containingJar = findContainingJar(this.getClass) match {
      case Some(jar) => jar
      case None => return
    }
    val reporterJar = findContainingJar(classOf[ILoopWrapperReporter]) match {
      case Some(jar) => jar
      case None => return
    }

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
        Seq(sourceJar.toPath),
        targetJar.toPath,
        xsbtiJars = Seq(interfaceJar, containingJar, reporterJar).distinct.map(_.toPath),
        id = replLabel,
        compiler = rawCompiler,
        log = logger
      )
    catch {
      case compilationFailed: sbt.internal.inc.CompileFailed =>
        val indent = "  "
        val message =
          s"""Repl wrapper compilation failed: $compilationFailed
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
  private def Scala2ILoopWrapperVersion = 6
  private def Scala3ILoopWrapperVersion = 11
  // 2.12 works OK for 2.11 as well
  private def ILoopWrapper212Impl   = ILoopWrapperDescriptor("ILoopWrapper212Impl", Scala2ILoopWrapperVersion)
  private def ILoopWrapper213_0Impl = ILoopWrapperDescriptor("ILoopWrapper213_0Impl", Scala2ILoopWrapperVersion)
  private def ILoopWrapper213Impl   = ILoopWrapperDescriptor("ILoopWrapper213Impl", Scala2ILoopWrapperVersion)
  private def ILoopWrapper3Impl     = ILoopWrapperDescriptor("ILoopWrapper3Impl", Scala3ILoopWrapperVersion)

  private def wrapperClassNameFor(version: ScalaVersion): ILoopWrapperDescriptor = {
    val v = version.value
    if (v.startsWith("2.13.0")) ILoopWrapper213_0Impl
    else if (v.startsWith("2.13")) ILoopWrapper213Impl
    else if (version.isScala3) ILoopWrapper3Impl
    else ILoopWrapper212Impl
  }

  private[worksheet] case class ReplWrapperCompiled(file: File, className: String, version: ScalaVersion)

  private[worksheet] case class ScalaVersion(value: String) {
    // temp solution while dotty is evolving very fast
    val isScala3: Boolean = value.startsWith("""3.""")
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

  private def createIsolatingClassLoader(compilerJars: CompilerJars): URLClassLoader = {
    val jars = compilerJars.allJars
    val parent = IsolatingClassLoader.scalaStdLibIsolatingLoader(this.getClass.getClassLoader)
    new URLClassLoader(sbt.io.Path.toURLs(jars), parent)
  }

  // use for debugging
  private class ClientDelegatingLogger(client: Client) extends Logger {
    override def trace(t: => Throwable): Unit = client.trace(t)
    override def success(message: => String): Unit = client.info(s"success: $message")
    override def log(level: Level.Value, message: => String): Unit = client.message(toMessageKind(level), message)

    private def toMessageKind(level: Level.Value): Kind = level match {
      case sbt.util.Level.Debug => Kind.INFO
      case sbt.util.Level.Info  => Kind.INFO
      case sbt.util.Level.Warn  => Kind.WARNING
      case Level.Error          => Kind.ERROR
    }
  }

  case class ReplContext(sbtData: SbtData,
                         compilerJars: CompilerJars,
                         classpath: Seq[File],
                         scalacOptions: Seq[String])
}