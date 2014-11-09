package org.jetbrains.jps.incremental.scala
package local

import java.io.File
import java.net.URLClassLoader

import org.jetbrains.jps.incremental.scala.data.{CompilerData, CompilerJars, SbtData}
import org.jetbrains.jps.incremental.scala.local.CompilerFactoryImpl._
import org.jetbrains.jps.incremental.scala.model.IncrementalityType
import sbt.compiler.{AggressiveCompile, AnalyzingCompiler, IC}
import sbt.inc.AnalysisStore
import sbt.{ClasspathOptions, Path, ScalaInstance}
import xsbti.{F0, Logger}

/**
 * @author Pavel Fatin
 */
class CompilerFactoryImpl(sbtData: SbtData) extends CompilerFactory {
  
  def createCompiler(compilerData: CompilerData, client: Client, fileToStore: File => AnalysisStore): Compiler = {

    val scalac: Option[AnalyzingCompiler] = getScalac(sbtData, compilerData.compilerJars, client)

    compilerData.incrementalType match {
      case IncrementalityType.SBT =>
        val javac = {
          val scala = getScalaInstance(compilerData.compilerJars)
                  .getOrElse(new ScalaInstance("stub", null, new File(""), new File(""), Seq.empty, None))
          val classpathOptions = ClasspathOptions.javac(compiler = false)
          AggressiveCompile.directOrFork(scala, classpathOptions, compilerData.javaHome)
        }
        new SbtCompiler(javac, scalac, fileToStore)
        
      case IncrementalityType.IDEA =>
        if (scalac.isDefined) new IdeaIncrementalCompiler(scalac.get)
        else throw new IllegalStateException("Could not create scalac instance")

    }

  }

  def getScalac(sbtData: SbtData, compilerJars: Option[CompilerJars], client: Client): Option[AnalyzingCompiler] = {
    getScalaInstance(compilerJars).map { scala =>
    val compiledIntefaceJar = getOrCompileInterfaceJar(sbtData.interfacesHome, sbtData.sourceJar,
        sbtData.interfaceJar, scala, sbtData.javaClassVersion, client)

      IC.newScalaCompiler(scala, compiledIntefaceJar, ClasspathOptions.javac(compiler = false), NullLogger)
    }
  }

  private def getScalaInstance(compilerJars: Option[CompilerJars]): Option[ScalaInstance] =
    compilerJars.map(createScalaInstance)
}

object CompilerFactoryImpl {
  private val scalaInstanceCache = new Cache[CompilerJars, ScalaInstance](3)
  
  private def createScalaInstance(jars: CompilerJars): ScalaInstance = {
    scalaInstanceCache.getOrUpdate(jars) {

      val classLoader = {
        val urls = Path.toURLs(jars.library +: jars.compiler +: jars.extra)
        new URLClassLoader(urls, sbt.classpath.ClasspathUtilities.rootLoader)
      }

      val version = readProperty(classLoader, "compiler.properties", "version.number")

      new ScalaInstance(version.getOrElse("unknown"), classLoader, jars.library, jars.compiler, jars.extra, version)
    }

  }

  private def getOrCompileInterfaceJar(home: File,
                                       sourceJar: File,
                                       interfaceJar: File,
                                       scalaInstance: ScalaInstance,
                                       javaClassVersion: String,
                                       client: Client): File = {

    val scalaVersion = scalaInstance.actualVersion
    val interfaceId = "compiler-interface-" + scalaVersion + "-" + javaClassVersion
    val targetJar = new File(home, interfaceId + ".jar")

    if (!targetJar.exists) {
      client.progress("Compiling Scalac " + scalaVersion + " interface")
      home.mkdirs()
      IC.compileInterfaceJar(interfaceId, sourceJar, targetJar, interfaceJar, scalaInstance, NullLogger)
    }

    targetJar
  }
}

private object NullLogger extends Logger {
  def error(p1: F0[String]) {}

  def warn(p1: F0[String]) {}

  def info(p1: F0[String]) {}

  def debug(p1: F0[String]) {}

  def trace(p1: F0[Throwable]) {}
}