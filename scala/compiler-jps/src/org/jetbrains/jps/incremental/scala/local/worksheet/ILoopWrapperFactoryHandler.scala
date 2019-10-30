package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.{File, OutputStream}
import java.lang.reflect.InvocationTargetException
import java.net.{URLClassLoader, URLDecoder}

import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.data.{CompilerJars, SbtData}
import org.jetbrains.jps.incremental.scala.local.worksheet.compatibility.{JavaClientProvider, JavaILoopWrapperFactory}
import org.jetbrains.jps.incremental.scala.local.worksheet.util.IsolatingClassLoader
import org.jetbrains.jps.incremental.scala.local.{CompilerFactoryImpl, NullLogger}
import org.jetbrains.jps.incremental.scala.remote.Arguments
import sbt.internal.inc.{AnalyzingCompiler, RawCompiler}
import sbt.io.Path
import xsbti.compile.{ClasspathOptionsUtil, ScalaInstance}

class ILoopWrapperFactoryHandler {
  import ILoopWrapperFactoryHandler._

  private var replFactory: (ClassLoader, JavaILoopWrapperFactory, String) = _

  def loadReplWrapperAndRun(commonArguments: Arguments, out: OutputStream,
                            @NotNull client: Client): Unit =  try {
    val compilerJars = commonArguments.compilerData.compilerJars.orNull
    val scalaInstance = CompilerFactoryImpl.createScalaInstance(compilerJars)
    val scalaVersion = findScalaVersionIn(scalaInstance)
    val iLoopFile = getOrCompileReplLoopFile(commonArguments.sbtData, scalaInstance, client)

    // TODO: improve caching, for now we can have only 1 instance with 1 version of scala
    replFactory match {
      case (_, _, oldVersion) if oldVersion == scalaVersion =>
      case _ =>
        val loader = createIsolatingClassLoader(compilerJars)
        val iLoopWrapper = new JavaILoopWrapperFactory
        replFactory = (loader, iLoopWrapper, scalaVersion)
    }

    client.progress("Running REPL...")

    val (classLoader, iLoopWrapper, _) = replFactory

    WorksheetServer.patchSystemOut(out)

    val clientProvider: JavaClientProvider = message => client.progress(message)
    iLoopWrapper.loadReplWrapperAndRun(
      scalaToJava(commonArguments.worksheetFiles),
      commonArguments.compilationData.sources.headOption.map(_.getName).getOrElse(""),
      compilerJars.library,
      compilerJars.compiler,
      scalaToJava(compilerJars.extra),
      scalaToJava(commonArguments.compilationData.classpath),
      out,
      iLoopFile,
      clientProvider,
      classLoader
    )
  } catch {
    case e: InvocationTargetException =>
      throw e.getTargetException
  }

  protected def getOrCompileReplLoopFile(sbtData: SbtData, scalaInstance: ScalaInstance, client: Client): File = {
    val version = findScalaVersionIn(scalaInstance)
    val is213 = version.startsWith("2.13")
    val iLoopWrapperClass = if (is213) "ILoopWrapper213Impl" else "ILoopWrapperImpl"
    val replLabel = s"repl-wrapper-$version-${sbtData.javaClassVersion}-$WRAPPER_VERSION-$iLoopWrapperClass.jar"
    val targetFile = new File(sbtData.interfacesHome, replLabel)

    if (!targetFile.exists()) {
      compileReplLoopFile(scalaInstance, sbtData, is213, replLabel, targetFile, client)
    }

    targetFile
  }

  private def compileReplLoopFile(scalaInstance: ScalaInstance, sbtData: SbtData, is213: Boolean, replLabel: String, targetFile: File, client: Client): Unit = {
    // sources containing ILoopWrapper213Impl.scala and ILoopWrapperImpl.scala
    val sourceJar = {
      val jpsJarsFolder = sbtData.sourceJars._2_11.getParent
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

    val logger = NullLogger
    sbtData.interfacesHome.mkdirs()

    def filter(file: File): Boolean = {
      val is213Impl = file.getName.endsWith("213Impl.scala")
      if (is213) is213Impl else !is213Impl
    }

    val rawCompiler = new RawCompiler(scalaInstance, ClasspathOptionsUtil.auto(), logger) {
      override def apply(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String]): Unit = {
        super.apply(sources.filter(filter), classpath, outputDirectory, options)
      }
    }
    AnalyzingCompiler.compileSources(
      Seq(sourceJar),
      targetFile,
      xsbtiJars = Seq(interfaceJar, containingJar, reporterJar).distinct,
      id = replLabel,
      compiler = rawCompiler,
      log = logger
    )
  }
}

object ILoopWrapperFactoryHandler {
  // ATTENTION: when editing ILoopWrapper213Impl.scala or ILoopWrapperImpl.scala ensure to increase the version
  private val WRAPPER_VERSION = 3

  private def findScalaVersionIn(scalaInstance: ScalaInstance): String =
    CompilerFactoryImpl.readScalaVersionIn(scalaInstance.loader).getOrElse("Undefined")

  private def findContainingJar(clazz: Class[_]): Option[File] = {
    val resource = clazz.getResource(s"/${clazz.getName.replace('.', '/')}.class")

    if (resource == null) return None

    val url = URLDecoder.decode(resource.toString.stripPrefix("jar:file:"), "UTF-8")
    val idx = url.indexOf(".jar!")
    if (idx == -1) return None

    Some(new File(url.substring(0, idx + 4))).filter(_.exists())
  }

  private def createIsolatingClassLoader(compilerJars: CompilerJars): URLClassLoader = {
    val jars = compilerJars.library +: compilerJars.compiler +: compilerJars.extra
    val parent = IsolatingClassLoader.scalaStdLibIsolatingLoader(this.getClass.getClassLoader)
    new URLClassLoader(Path.toURLs(jars), parent)
  }

  //We need this method as scala std lib converts scala collections to its own wrappers with asJava method
  private def scalaToJava[T](seq: Seq[T]): java.util.List[T] = {
    val list = new java.util.ArrayList[T]()
    seq.foreach(list.add)
    list
  }
}