package org.jetbrains.jps.incremental.scala

import java.io.File
import sbt.{ClasspathOptions, ScalaInstance}
import java.net.URLClassLoader
import sbt.Path._
import xsbti.Logger
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.messages.ProgressMessage
import sbt.compiler.{AggressiveCompile, IC}
import java.util.Properties
import CompilerFactoryImpl._
import sbt.inc.AnalysisStore

/**
 * @author Pavel Fatin
 */
class CompilerFactoryImpl(interfaceJar: File, sourceJar: File, interfacesHome: File) extends CompilerFactory {
  private lazy val preciseInterfacesHome = {
    val sbtVersion = readSbtVersion(getClass.getClassLoader).getOrElse("unknown")
    new File(interfacesHome, sbtVersion)
  }

  private lazy val javaClassVersion = System.getProperty("java.class.version")

  def createCompiler(configuration: CompilerConfiguration,
                     storeProvider: File => AnalysisStore,
                     messageHandler: MessageHandler): Compiler = {

    val scalaInstance = createScalaInstance(configuration.compilerJar, configuration.libraryJar, configuration.extraJars)

    val scalac = {
      val logger = new MessageHandlerLogger("scala", messageHandler)

      val compiledIntefaceJar = getOrCompileInterfaceJar(preciseInterfacesHome, sourceJar, interfaceJar,
        scalaInstance, javaClassVersion, logger, messageHandler)

      IC.newScalaCompiler(scalaInstance, compiledIntefaceJar, ClasspathOptions.boot, logger)
    }

    val javac = AggressiveCompile.directOrFork(scalaInstance,
      ClasspathOptions.javac(compiler = false), Some(configuration.javaHome))

    new CompilerImpl(scalac, javac, storeProvider)
  }
}

object CompilerFactoryImpl {
  def createScalaInstance(compilerJar: File, libraryJar: File, extraJars: Seq[File]): ScalaInstance = {
    val classLoader = {
      val urls = toURLs(compilerJar +: libraryJar +: extraJars)
      new URLClassLoader(urls, sbt.classpath.ClasspathUtilities.rootLoader)
    }

    val version = CompilerFactoryImpl.readProperty(classLoader, "compiler.properties", "version.number")

    new ScalaInstance(version.getOrElse("unknown"), classLoader, libraryJar, compilerJar, extraJars, version)
  }

  def getOrCompileInterfaceJar(home: File, sourceJar: File, interfaceJar: File, scalaInstance: ScalaInstance,
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

  def readSbtVersion(classLoader: ClassLoader): Option[String] = {
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

  def readProperty(classLoader: ClassLoader, resource: String, name: String): Option[String] = {
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
