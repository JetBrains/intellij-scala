package org.jetbrains.jps.incremental.scala
package local

import java.io.File
import java.net.URLClassLoader

import org.jetbrains.jps.incremental.scala.data.{CompilerData, CompilerJars, SbtData}
import org.jetbrains.jps.incremental.scala.local.CompilerFactoryImpl._
import org.jetbrains.jps.incremental.scala.model.IncrementalityType
import xsbti.compile.{ScalaInstance => _, _}
import sbt.io.Path
import sbt.util.Logger
import sbt.internal.inc._
import sbt.internal.inc.javac.JavaTools
import sbt.internal.inc.classpath.ClassLoaderCache

/**
 * @author Pavel Fatin
 */
class CompilerFactoryImpl(sbtData: SbtData) extends CompilerFactory {
  
  def createCompiler(compilerData: CompilerData, client: Client, fileToStore: File => AnalysisStore): Compiler = {

    val scalac: Option[AnalyzingCompiler] = getScalac(sbtData, compilerData.compilerJars, client)

    compilerData.compilerJars match {
      case Some(jars) if jars.dotty.isDefined =>
        return new DottyCompiler(createScalaInstance(jars), jars)
      case _ =>
    }

    compilerData.incrementalType match {
      case IncrementalityType.SBT =>
        val javac = {
          val scala = getScalaInstance(compilerData.compilerJars)
                  .getOrElse(new ScalaInstance("stub", null, new File(""), new File(""), Array.empty, None))
          val classpathOptions = ClasspathOptionsUtil.javac(false)
          JavaTools.directOrFork(scala, classpathOptions, compilerData.javaHome)
        }
        new SbtCompiler(javac, scalac, fileToStore)
        
      case IncrementalityType.IDEA =>
        if (scalac.isDefined) new IdeaIncrementalCompiler(scalac.get)
        else throw new IllegalStateException("Could not create scalac instance")

    }

  }

  private val classloaderCache = Some(new ClassLoaderCache(new URLClassLoader(Array())))

  def getScalac(sbtData: SbtData, compilerJars: Option[CompilerJars], client: Client): Option[AnalyzingCompiler] = {
    getScalaInstance(compilerJars).map { scala =>
      val compiledInterfaceJar = getOrCompileInterfaceJar(sbtData.interfacesHome, sbtData.sourceJar,
        Seq(sbtData.sbtInterfaceJar, sbtData.compilerInterfaceJar), scala, sbtData.javaClassVersion, client)

      new AnalyzingCompiler(scala,
        ZincCompilerUtil.constantBridgeProvider(scala, compiledInterfaceJar),
        ClasspathOptionsUtil.javac(false), _ => (), classloaderCache)
    }
  }

  private def getScalaInstance(compilerJars: Option[CompilerJars]): Option[ScalaInstance] =
    compilerJars.map(createScalaInstance)
}

object CompilerFactoryImpl {
  private val scalaInstanceCache = new Cache[CompilerJars, ScalaInstance](3)

  var classLoadersMap = Map[Seq[File], ClassLoader]()

  private def createScalaInstance(jars: CompilerJars): ScalaInstance = {
    scalaInstanceCache.getOrUpdate(jars) {

      val paths = jars.library +: jars.compiler +: jars.extra

      def createClassLoader() = {
        val urls = Path.toURLs(paths)
        val newClassloader = new URLClassLoader(urls, sbt.internal.inc.classpath.ClasspathUtilities.rootLoader)

        classLoadersMap += paths -> newClassloader

        newClassloader
      }

      val classLoader = synchronized(classLoadersMap.getOrElse(paths, createClassLoader()))

      val version = readProperty(classLoader, "compiler.properties", "version.number")

      new ScalaInstance(version.getOrElse("unknown"), classLoader, jars.library, jars.compiler, jars.extra.toArray, version)
    }

  }

  private def getOrCompileInterfaceJar(home: File,
                                       sourceJar: File,
                                       interfaceJars: Seq[File],
                                       scalaInstance: ScalaInstance,
                                       javaClassVersion: String,
                                       client: Client): File = {

    val scalaVersion = scalaInstance.actualVersion
    val interfaceId = "compiler-interface-" + scalaVersion + "-" + javaClassVersion
    val targetJar = new File(home, interfaceId + ".jar")

    if (!targetJar.exists) {
      client.progress("Compiling Scalac " + scalaVersion + " interface")
      home.mkdirs()
      val raw = new RawCompiler(scalaInstance, ClasspathOptionsUtil.auto, NullLogger)
      AnalyzingCompiler.compileSources(sourceJar :: Nil, targetJar, interfaceJars, interfaceId, raw, NullLogger)
    }

    targetJar
  }
}

private object NullLogger extends Logger {
  override def log(level: sbt.util.Level.Value,message: => String): Unit = {}
  override def success(message: => String): Unit = {}
  override def trace(t: => Throwable): Unit = {}
}