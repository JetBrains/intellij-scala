package org.jetbrains.jps.incremental.scala

import data.{CompilerJars, CompilerData, SbtData}
import java.io.File
import java.net.URLClassLoader
import sbt.{ClasspathOptions, ScalaInstance, Path}
import sbt.compiler.{AggressiveCompile, IC}
import xsbti.Logger
import CompilerFactoryImpl._
import sbt.inc.AnalysisStore

/**
 * @author Pavel Fatin
 */
class CompilerFactoryImpl(sbtData: SbtData) extends CompilerFactory {
  def createCompiler(compilerData: CompilerData, client: Client, fileToStore: File => AnalysisStore): Compiler = {
    val scalaInstance = compilerData.compilerJars.map(createScalaInstance)

    val scalac = scalaInstance.map { scala =>
      val logger = new ClientLogger(client)

      val compiledIntefaceJar = getOrCompileInterfaceJar(sbtData.interfacesHome, sbtData.sourceJar,
        sbtData.interfaceJar, scala, sbtData.javaClassVersion, logger, client)

      IC.newScalaCompiler(scala, compiledIntefaceJar, ClasspathOptions.boot, logger)
    }

    val javac = {
      val scala = scalaInstance.getOrElse(new ScalaInstance("stub", null, new File(""), new File(""), Seq.empty, None))

      val classpathOptions = ClasspathOptions.javac(compiler = false)

      AggressiveCompile.directOrFork(scala, classpathOptions, Some(compilerData.javaHome))
    }

    new CompilerImpl(javac, scalac, fileToStore)
  }
}

object CompilerFactoryImpl {
  private def createScalaInstance(jars: CompilerJars): ScalaInstance = {
    val classLoader = {
      val urls = Path.toURLs(jars.library +: jars.compiler +: jars.extra)
      new URLClassLoader(urls, sbt.classpath.ClasspathUtilities.rootLoader)
    }

    val version = readProperty(classLoader, "compiler.properties", "version.number")

    new ScalaInstance(version.getOrElse("unknown"), classLoader, jars.library, jars.compiler, jars.extra, version)
  }

  private def getOrCompileInterfaceJar(home: File, sourceJar: File, interfaceJar: File, scalaInstance: ScalaInstance,
                                       javaClassVersion: String, log: Logger, client: Client): File = {
    val scalaVersion = scalaInstance.actualVersion
    val interfaceId = "compiler-interface-" + scalaVersion + "-" + javaClassVersion
    val targetJar = new File(home, interfaceId + ".jar")

    if (!targetJar.exists) {
      client.progress("Compiling Scalac " + scalaVersion + " interface")
      home.mkdirs()
      IC.compileInterfaceJar(interfaceId, sourceJar, targetJar, interfaceJar, scalaInstance, log)
    }

    targetJar
  }
}
