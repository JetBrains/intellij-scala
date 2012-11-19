package org.jetbrains.jps.incremental.scala

import data.{CompilationData, JavaData, SbtData}
import org.jetbrains.jps.incremental.MessageHandler
import java.io.File
import sbt._
import collection.JavaConverters._
import inc.{AnalysisFormats, FileBasedStore, AnalysisStore, Locate}
import java.net.URLClassLoader
import sbt.Path._
import compiler.{CompilerCache, AggressiveCompile, IC}
import xsbti.compile.CompileOrder
import org.jetbrains.jps.incremental.messages.ProgressMessage
import xsbti.Logger
import scala.Some
import java.util.Properties

/**
 * @author Pavel Fatin
 */
class Compiler(compilerName: String, messageHandler: MessageHandler, fileHandler: FileHandler) {
  private val logger = new MessageHandlerLogger(compilerName, messageHandler)
  private val callback = new Analyzer(compilerName, messageHandler, fileHandler)

  def compile(sources: Array[File], sbtData: SbtData, javaData: JavaData, compilationData: CompilationData, cacheFile: File) {
    val compilerClasspath = compilationData.getScalaCompilerClasspath.asScala.toSeq

    val scalaInstance = Compiler.createScalaInstance(compilerClasspath)

    val compilationClasspath = compilationData.getCompilationClasspath.asScala.toSeq

    val compileOptions = new CompileOptions(Nil, Nil)
    val compileSetup = new CompileSetup(compilationData.getOutputDirectory, compileOptions, scalaInstance.version, CompileOrder.ScalaThenJava)
    val analysisStore = Compiler.createAnalysisStore(cacheFile)

    val scalac = {
      val compiledIntefaceJar = Compiler.getOrCompileInterfaceJar(sbtData.getCompilerInterfacesHome,
        sbtData.getCompilerInterfaceSources, sbtData.getSbtInterface, scalaInstance, logger, messageHandler)

      IC.newScalaCompiler(scalaInstance, compiledIntefaceJar, ClasspathOptions.boot, logger)
    }

    val javac = AggressiveCompile.directOrFork(scalaInstance, ClasspathOptions.javac(compiler = false), Some(javaData.getHome))

    val compiler = new AggressiveCompile(cacheFile)

    //    val reporter = new LoggerReporter(Int.MaxValue, logger)

    messageHandler.processMessage(new ProgressMessage("Compiling..."))

    compiler.compile1(sources, compilationClasspath, compileSetup, analysisStore, Function.const(None), Locate.definesClass,
      scalac, javac, 100, false, CompilerCache.fresh, callback)(logger)
  }
}

object Compiler {
  private val CompilerInterfaceId = "compiler-interface"
  private val JavaClassVersion = System.getProperty("java.class.version")

  private def createScalaInstance(classpath: Seq[File]): ScalaInstance = {
    val libraryJar = classpath.find(_.getName == "scala-library.jar")
            .getOrElse(throw new ConfigurationException("No scala-library.jar in Scala compiler library"))

    val compilerJar = classpath.find(_.getName == "scala-compiler.jar")
            .getOrElse(throw new ConfigurationException("No scala-compiler.jar in Scala compiler library"))

    val extraJars = classpath.filterNot(it => it == libraryJar || it == compilerJar)

    val classLoader = new URLClassLoader(toURLs(classpath), sbt.classpath.ClasspathUtilities.rootLoader)

    val version = readProperty(classLoader, "compiler.properties", "version.number")

    new ScalaInstance(version.getOrElse("unknown"), classLoader, libraryJar, compilerJar, extraJars, version)
  }

  private def createAnalysisStore(cacheFile: File): AnalysisStore = {
    import sbinary.DefaultProtocol.{immutableMapFormat, immutableSetFormat, StringFormat, tuple2Format}
    import sbt.inc.AnalysisFormats._
    val store = FileBasedStore(cacheFile)(AnalysisFormats.analysisFormat, AnalysisFormats.setupFormat)
    AnalysisStore.sync(AnalysisStore.cached(store))
  }

  private def getOrCompileInterfaceJar(home: File, sourceJar: File, interfaceJar: File, scalaInstance: ScalaInstance,
                                       log: Logger, messageHandler: MessageHandler): File = {
    val scalaVersion = scalaInstance.actualVersion
    val interfaceId = CompilerInterfaceId + "-" + scalaVersion + "-" + JavaClassVersion
    val targetJar = new File(home, interfaceId + ".jar")

    if (!targetJar.exists) {
      messageHandler.processMessage(new ProgressMessage("Compiling Scalac " + scalaVersion + " interface"))
      home.mkdirs()
      IC.compileInterfaceJar(interfaceId, sourceJar, targetJar, interfaceJar, scalaInstance, log)
    }

    targetJar
  }

  private def readProperty(classLoader: ClassLoader, resource: String, name: String): Option[String] = {
    val stream = classLoader.getResourceAsStream(resource)

    try {
      val properties = new Properties()
      properties.load(stream)
      Option(properties.getProperty(name))
    } finally {
      stream.close()
    }
  }
}
