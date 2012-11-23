package org.jetbrains.jps.incremental.scala

import data.{CompilerData, SbtData}
import java.io.File
import java.net.URLClassLoader
import sbt.{ClasspathOptions, ScalaInstance, Path}
import sbt.compiler.{AggressiveCompile, IC}
import xsbti.Logger
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.messages.ProgressMessage
import CompilerFactoryImpl._
import sbt.inc.AnalysisStore

/**
 * @author Pavel Fatin
 */
class CompilerFactoryImpl(sbtData: SbtData) extends CompilerFactory {
  def createCompiler(compilerData: CompilerData,
                     storeProvider: File => AnalysisStore,
                     messageHandler: MessageHandler): Compiler = {

    val scalaInstance = createScalaInstance(compilerData.libraryJar, compilerData.compilerJar, compilerData.extraJars)

    val scalac = {
      val logger = new MessageHandlerLogger("scala", messageHandler)

      val compiledIntefaceJar = getOrCompileInterfaceJar(sbtData.interfacesHome, sbtData.sourceJar,
        sbtData.interfaceJar, scalaInstance, sbtData.javaClassVersion, logger, messageHandler)

      IC.newScalaCompiler(scalaInstance, compiledIntefaceJar, ClasspathOptions.boot, logger)
    }

    val javac = AggressiveCompile.directOrFork(scalaInstance,
      ClasspathOptions.javac(compiler = false), Some(compilerData.javaHome))

    new CompilerImpl(scalac, javac, storeProvider)
  }
}

object CompilerFactoryImpl {
  private def createScalaInstance(libraryJar: File, compilerJar: File, extraJars: Seq[File]): ScalaInstance = {
    val classLoader = {
      val urls = Path.toURLs(libraryJar +: compilerJar +: extraJars)
      new URLClassLoader(urls, sbt.classpath.ClasspathUtilities.rootLoader)
    }

    val version = readProperty(classLoader, "compiler.properties", "version.number")

    new ScalaInstance(version.getOrElse("unknown"), classLoader, libraryJar, compilerJar, extraJars, version)
  }

  private def getOrCompileInterfaceJar(home: File, sourceJar: File, interfaceJar: File, scalaInstance: ScalaInstance,
                               javaClassVersion: String, log: Logger, messageHandler: MessageHandler): File = {
    val scalaVersion = scalaInstance.actualVersion
    val interfaceId = "compiler-interface-" + scalaVersion + "-" + javaClassVersion
    val targetJar = new File(home, interfaceId + ".jar")

    if (!targetJar.exists) {
      messageHandler.processMessage(new ProgressMessage("Compiling Scalac " + scalaVersion + " interface"))
      home.mkdirs()
      IC.compileInterfaceJar(interfaceId, sourceJar, targetJar, interfaceJar, scalaInstance, log)
    }

    targetJar
  }
}
