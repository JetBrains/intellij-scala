package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.{File, PrintStream}
import java.lang.reflect.InvocationTargetException
import java.net.{URLClassLoader, URLDecoder}

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.local.{CompilerFactoryImpl, NullLogger}
import org.jetbrains.jps.incremental.scala.local.worksheet.compatibility.{JavaClientProvider, JavaILoopWrapperFactory}
import org.jetbrains.jps.incremental.scala.local.worksheet.util.IsolatingClassLoader
import sbt.internal.inc.{AnalyzingCompiler, RawCompiler}
import sbt.io.Path
import sbt.util.{Level, Logger}
import xsbti.compile.{ClasspathOptionsUtil, ScalaInstance}
import org.jetbrains.jps.incremental.scala.compilerVersion
import org.jetbrains.plugins.scala.compiler.data.{Arguments, CompilerJars, SbtData}

class ILoopWrapperFactoryHandler {
  import ILoopWrapperFactoryHandler._

  private var replFactory: (ClassLoader, JavaILoopWrapperFactory, String) = _

  def loadReplWrapperAndRun(
    commonArguments: Arguments,
    out: PrintStream, // the consumed output is delegated to client then encoded and delegated to Nailgun out and sent to the
    client: Client
  ): Unit = try {
    val compilerJars = commonArguments.compilerData.compilerJars.orNull
    val scalaInstance = CompilerFactoryImpl.createScalaInstance(compilerJars)
    val scalaVersion = findScalaVersionIn(scalaInstance)
    val (iLoopFile, isScala3) = getOrCompileReplLoopFile(commonArguments.sbtData, scalaInstance, client)

    // TODO: improve caching, for now we can have only 1 instance with 1 version of scala
    replFactory match {
      case (_, _, oldVersion) if oldVersion == scalaVersion =>
      case _ =>
        val loader = createIsolatingClassLoader(compilerJars)
        val iLoopWrapper = new JavaILoopWrapperFactory
        replFactory = (loader, iLoopWrapper, scalaVersion)
    }

    // TODO: extract to bundle carefully,
    //  DynamicBundle isn't available in JSP process, we could pass ADT instead of strings
    client.progress("Running REPL...")

    val (classLoader, iLoopWrapper, _) = replFactory

    WorksheetServer.patchSystemOut(out)

    val clientProvider: JavaClientProvider = new JavaClientProvider  {
      override def onProgress(message: String): Unit = client.progress(message)
      override def onInitializationException(ex: Exception): Unit = client.trace(ex)
    }
    iLoopWrapper.loadReplWrapperAndRun(
      scalaToJava(commonArguments.worksheetFiles),
      scalaToJava(commonArguments.compilationData.scalaOptions),
      commonArguments.compilationData.sources.headOption.map(_.getName).getOrElse(""),
      compilerJars.library,
      compilerJars.compiler,
      scalaToJava(compilerJars.extra),
      scalaToJava(commonArguments.compilationData.classpath),
      out,
      iLoopFile,
      isScala3,
      clientProvider,
      classLoader
    )
  } catch {
    case e: InvocationTargetException =>
      throw e.getTargetException
  }

  protected def getOrCompileReplLoopFile(sbtData: SbtData, scalaInstance: ScalaInstance, client: Client): (File, Boolean) = {
    val version = findScalaVersionIn(scalaInstance)
    val isScala3 = isScala3Version(version)
    val (iLoopWrapperClass, WrapperVersion(wrapperVersion)) =
      if (version.startsWith("2.13.0")) ILoopWrapper213_0Impl
      else if (version.startsWith("2.13")) ILoopWrapper213Impl
      else if (isScala3) ILoopWrapper3Impl
      else ILoopWrapperImpl
    val replLabel = s"repl-wrapper-$version-${sbtData.javaClassVersion}-$wrapperVersion-$iLoopWrapperClass.jar"
    val targetFile = new File(sbtData.interfacesHome, replLabel)

    if (!targetFile.exists()) {
      compileReplLoopFile(scalaInstance, sbtData, iLoopWrapperClass, replLabel, targetFile, client)
    }

    (targetFile, isScala3)
  }

  // temp solution while dotty is evolving very fast
  private def isScala3Version(version: String): Boolean = version.matches("""0\.2\d.*""")

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
  val ILoopWrapper3Impl     = ("ILoopWrapper3Impl", WrapperVersion(2))

  private def findScalaVersionIn(scalaInstance: ScalaInstance): String =
    compilerVersion(scalaInstance.loader).getOrElse("Undefined")

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
    new URLClassLoader(Path.toURLs(jars), parent)
  }

  //We need this method as scala std lib converts scala collections to its own wrappers with asJava method
  private def scalaToJava[T](seq: Seq[T]): java.util.List[T] = {
    val list = new java.util.ArrayList[T]()
    seq.foreach(list.add)
    list
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
}