package org.jetbrains.jps.incremental.scala

import data.{CompilationData, JavaData, SbtData}
import org.jetbrains.jps.incremental.MessageHandler
import java.io.File
import sbt._
import inc.{AnalysisFormats, FileBasedStore, AnalysisStore, Locate}
import java.net.URLClassLoader
import sbt.Path._
import compiler.{CompileOutput, CompilerCache, AggressiveCompile, IC}
import xsbti.compile.{CompileProgress, CompileOrder}
import org.jetbrains.jps.incremental.messages.ProgressMessage
import xsbti._
import java.util.Properties
import xsbti.Logger

/**
 * @author Pavel Fatin
 */
class Compiler(compilerName: String, messageHandler: MessageHandler, fileHandler: FileHandler, progress: CompileProgress) {
  private val logger = new MessageHandlerLogger(compilerName, messageHandler)
  private val callback = new Analyzer(compilerName, messageHandler, fileHandler)

  def compile(sources: Array[File], sbtData: SbtData, javaData: JavaData, compilationData: CompilationData, cacheFile: File) {
    try {
      doCompile(sources, sbtData, javaData, compilationData, cacheFile)
    } catch {
      case _: CompileFailed => // the problem should be already reported
    }
  }

  private def doCompile(sources: Array[File], sbtData: SbtData, javaData: JavaData, compilationData: CompilationData, cacheFile: File) {
    val scalaInstance = {
      val compilerClasspath = compilationData.getScalaCompilerClasspath
      Compiler.createScalaInstance(compilerClasspath)
    }

    val compilationClasspath = compilationData.getCompilationClasspath

    val compileSetup = {
      val options = new CompileOptions(Nil, Nil)
      val output = CompileOutput(compilationData.getOutputDirectory)
      val order = if (compilationData.isScalaFirst) CompileOrder.ScalaThenJava else CompileOrder.JavaThenScala
      new CompileSetup(output, options, scalaInstance.version, order)
    }

    val analysisStore = Compiler.createAnalysisStore(cacheFile)

    val scalac = {
      val sbtVersion = Compiler.readSbtVersion(getClass.getClassLoader).getOrElse("unknown")

      val compiledIntefaceJar = Compiler.getOrCompileInterfaceJar(sbtVersion, sbtData.getCompilerInterfacesHome,
        sbtData.getCompilerInterfaceSources, sbtData.getSbtInterface, scalaInstance, logger, messageHandler)

      IC.newScalaCompiler(scalaInstance, compiledIntefaceJar, ClasspathOptions.boot, logger)
    }

    val javac = AggressiveCompile.directOrFork(scalaInstance, ClasspathOptions.javac(compiler = false), Some(javaData.getHome))

    val compiler = new AggressiveCompile(cacheFile)

    messageHandler.processMessage(new ProgressMessage("Compiling..."))

    val reporter = new ProblemReporter(compilerName, messageHandler)

    compiler.compile1(sources, compilationClasspath, compileSetup, Some(progress), analysisStore, Function.const(None), Locate.definesClass,
      scalac, javac, reporter, false, CompilerCache.fresh, Some(callback))(logger)
  }
}

private object Compiler {
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

  private def getOrCompileInterfaceJar(sbtVersion: String, home: File, sourceJar: File, interfaceJar: File,
                                       scalaInstance: ScalaInstance, log: Logger, messageHandler: MessageHandler): File = {
    val scalaVersion = scalaInstance.actualVersion
    val interfaceId = CompilerInterfaceId + "-" + scalaVersion + "-" + JavaClassVersion
    val targetJar = new File(new File(home, sbtVersion), interfaceId + ".jar")

    if (!targetJar.exists) {
      messageHandler.processMessage(new ProgressMessage("Compiling Scalac " + scalaVersion + " interface"))
      home.mkdirs()
      IC.compileInterfaceJar(interfaceId, sourceJar, targetJar, interfaceJar, scalaInstance, log)
    }

    targetJar
  }

  private def readSbtVersion(classLoader: ClassLoader): Option[String] = {
    readProperty(classLoader, "xsbt.version.properties", "version").map { version =>
      if (version.endsWith("-SNAPSHOT")) {
        readProperty(getClass.getClassLoader, "xsbt.version.properties", "timestamp")
                .map(timestamp => version + "-" + timestamp)
                .getOrElse(version)
      } else {
        version
      }
    }
  }

  private def readProperty(classLoader: ClassLoader, resource: String, name: String): Option[String] = {
    Option(classLoader.getResourceAsStream(resource)).flatMap { stream =>
      try {
        val properties = new Properties()
        properties.load(stream)
        Option(properties.getProperty(name))
      } finally {
        stream.close()
      }
    }
  }
}
