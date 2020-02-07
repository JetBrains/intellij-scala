package org.jetbrains.jps.incremental.scala
package local

import java.io.File
import java.net.URLClassLoader

import com.intellij.openapi.diagnostic.{Logger => JpsLogger}
import org.jetbrains.jps.incremental.scala.local.CompilerFactoryImpl._
import org.jetbrains.plugins.scala.compiler.IncrementalityType
import org.jetbrains.plugins.scala.compiler.data.{CompilerData, CompilerJars, SbtData}
import sbt.internal.inc._
import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.internal.inc.javac.JavaTools
import sbt.io.Path
import sbt.util.Logger
import xsbti.compile.{ScalaInstance => _, _}

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
    getScalaInstance(compilerJars).map { scalaInstance =>
      val compiledInterfaceJar = getOrCompileInterfaceJar(
        home = sbtData.interfacesHome,
        compilerBridges = sbtData.compilerBridges,
        interfaceJars = Seq(sbtData.sbtInterfaceJar, sbtData.compilerInterfaceJar),
        scalaInstance = scalaInstance,
        javaClassVersion = sbtData.javaClassVersion,
        client = Option(client),
        isDotty = compilerJars.exists(_.hasDotty)
      )

      new AnalyzingCompiler(
        scalaInstance,
        ZincCompilerUtil.constantBridgeProvider(scalaInstance, compiledInterfaceJar),
        ClasspathOptionsUtil.javac(false), _ => (),
        classloaderCache
      )
    }
  }

  private def getScalaInstance(compilerJars: Option[CompilerJars]): Option[ScalaInstance] =
    compilerJars.map(createScalaInstance)
}

object CompilerFactoryImpl {
  private val Log: JpsLogger = JpsLogger.getInstance(CompilerFactoryImpl.getClass.getName)
  private val scalaInstanceCache = new Cache[CompilerJars, ScalaInstance](3)

  private var classLoadersMap = Map[Seq[File], ClassLoader]()

  def createScalaInstance(jars: CompilerJars): ScalaInstance = {
    scalaInstanceCache.getOrUpdate(jars) {
      val paths = jars.allJars

      def createClassLoader() = {
        val urls = Path.toURLs(paths)
        val newClassloader = new URLClassLoader(urls, sbt.internal.inc.classpath.ClasspathUtilities.rootLoader)

        classLoadersMap += paths -> newClassloader

        newClassloader
      }

      val classLoader = synchronized(classLoadersMap.getOrElse(paths, createClassLoader()))

      val version = compilerVersion(classLoader)

      new ScalaInstance(version.getOrElse("unknown"), classLoader, jars.library, jars.compiler, jars.extra.toArray, version)
    }
  }

  private def getOrCompileInterfaceJar(home: File,
                                       compilerBridges: SbtData.CompilerBridges,
                                       interfaceJars: Seq[File],
                                       scalaInstance: ScalaInstance,
                                       javaClassVersion: String,
                                       client: Option[Client],
                                       isDotty: Boolean): File =
    if (isDotty) {
      compilerBridges.dotty._0_22
    } else {
      val scalaVersion = scalaInstance.actualVersion

      val sourceJar =
        if (isBefore_2_11(scalaVersion)) compilerBridges.scala._2_10
        else if (isBefore_2_13(scalaVersion)) compilerBridges.scala._2_11
        else compilerBridges.scala._2_13

      val interfaceId = "compiler-interface-" + scalaVersion + "-" + javaClassVersion
      val targetJar = new File(home, interfaceId + ".jar")

      if (!targetJar.exists) {
        client.foreach(_.progress("Compiling Scalac " + scalaVersion + " interface"))
        home.mkdirs()
        val raw = new RawCompiler(scalaInstance, ClasspathOptionsUtil.auto, NullLogger)
        AnalyzingCompiler.compileSources(sourceJar :: Nil, targetJar, interfaceJars, interfaceId, raw, NullLogger)
      }

      targetJar
    }

  private def isBefore_2_11(version: String): Boolean = version.startsWith("2.10") || !version.startsWith("2.1")

  private def isBefore_2_13(version: String): Boolean = version.startsWith("2.11") || version.startsWith("2.12")
}

object NullLogger extends Logger {
  override def log(level: sbt.util.Level.Value, message: => String): Unit = {}

  override def success(message: => String): Unit = {}

  override def trace(t: => Throwable): Unit = {}
}