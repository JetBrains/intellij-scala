package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.{File, PrintStream}
import java.lang.reflect.InvocationTargetException
import java.net.{URLClassLoader, URLDecoder}

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.local.worksheet.util.{IOUtils, IsolatingClassLoader}
import org.jetbrains.jps.incremental.scala.local.{CompilerFactoryImpl, NullLogger}
import org.jetbrains.jps.incremental.scala.{Client, compilerVersion}
import org.jetbrains.plugins.scala.compiler.data.worksheet.WorksheetArgs
import org.jetbrains.plugins.scala.compiler.data.{CompilerJars, SbtData}
import sbt.internal.inc.{AnalyzingCompiler, RawCompiler}
import sbt.io.Path
import sbt.util.{Level, Logger}
import xsbti.compile.{ClasspathOptionsUtil, ScalaInstance}

class ILoopWrapperFactoryHandler {
  import ILoopWrapperFactoryHandler._

  private var cachedReplFactory: (ClassLoader, ILoopWrapperFactory, ScalaVersion) = _

  def loadReplWrapperAndRun(
    args: WorksheetArgs.RunRepl,
    replContext: ReplContext,
    out: PrintStream, // the consumed output is delegated to client then encoded and delegated to Nailgun out and sent to the
    client: Client
  ): Unit = try {
    val compilerJars = replContext.compilerJars
    val scalaVersion = compilerVersion(compilerJars.compiler).map(ScalaVersion).getOrElse(FallBackScalaVersion)
    val replWrapper  = getOrCompileReplWrapper(replContext, scalaVersion, client)

    // TODO: improve caching, for now we can have only 1 instance with 1 version of scala
    cachedReplFactory match {
      case (_, _, oldVersion) if oldVersion == scalaVersion =>
      case _ =>
        val loader = createIsolatingClassLoader(compilerJars)
        val iLoopWrapper = new ILoopWrapperFactory
        if (cachedReplFactory != null) {
          cachedReplFactory._2.clearCaches()
        }
        cachedReplFactory = (loader, iLoopWrapper, scalaVersion)
    }

    client.progress("Running REPL...")

    val (classLoader, replFactory, _) = cachedReplFactory

    IOUtils.patchSystemOut(out)
    replFactory.loadReplWrapperAndRun(args, replContext, out, replWrapper, client, classLoader)
  } catch {
    case e: InvocationTargetException =>
      throw e.getTargetException
  }

  protected def getOrCompileReplWrapper(replContext: ReplContext, scalaVersion: ScalaVersion, client: Client): ReplWrapperCompiled = {
    val ReplContext(sbtData, compilerJars, _, _) = replContext
    val (wrapperClassName, WrapperVersion(wrapperVersion)) = wrapperClassNameFor(scalaVersion)
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
      override def apply(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String]): Unit = {
        val sourcesFiltered = sources.filter(filter)
        super.apply(sourcesFiltered, classpath, outputDirectory, options)
      }
    }
    AnalyzingCompiler.compileSources(
      Seq(sourceJar),
      targetJar,
      xsbtiJars = Seq(interfaceJar, containingJar, reporterJar).distinct,
      id = replLabel,
      compiler = rawCompiler,
      log = logger
    )
  }
}

//noinspection TypeAnnotation
object ILoopWrapperFactoryHandler {

  // ATTENTION: when editing ILoopWrapper213Impl.scala or ILoopWrapperImpl.scala ensure to increase the version
  case class WrapperVersion(value: Int)
  private val Scala2ILoopWrapperVersion = 5
  val ILoopWrapperImpl      = ("ILoopWrapperImpl", WrapperVersion(Scala2ILoopWrapperVersion))
  val ILoopWrapper213_0Impl = ("ILoopWrapper213_0Impl", WrapperVersion(Scala2ILoopWrapperVersion))
  val ILoopWrapper213Impl   = ("ILoopWrapper213Impl", WrapperVersion(Scala2ILoopWrapperVersion))
  val ILoopWrapper3Impl     = ("ILoopWrapper3Impl", WrapperVersion(9))

  private def wrapperClassNameFor(version: ScalaVersion): (String, WrapperVersion) = {
    val v = version.value
    if (v.startsWith("2.13.0")) ILoopWrapper213_0Impl
    else if (v.startsWith("2.13")) ILoopWrapper213Impl
    else if (version.isScala3) ILoopWrapper3Impl
    else ILoopWrapperImpl
  }

  private[worksheet] case class ReplWrapperCompiled(file: File, className: String, version: ScalaVersion)

  private[worksheet] case class ScalaVersion(value: String) {
    // temp solution while dotty is evolving very fast
    val isScala3: Boolean = value.matches("""0\.2\d.*""")
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
    new URLClassLoader(Path.toURLs(jars.toSeq), parent)
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
                         classpath: collection.Seq[File],
                         scalacOptions: collection.Seq[String])
}