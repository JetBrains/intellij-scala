package org.jetbrains.jps.incremental.scala
package local

import org.jetbrains.jps.incremental.scala.local.CompilerFactoryImpl._
import org.jetbrains.plugins.scala.compiler.data.{CompilerData, CompilerJars, IncrementalityType, SbtData}
import sbt.internal.inc._
import sbt.internal.inc.classpath.{ClassLoaderCache, ClasspathUtil}
import sbt.internal.inc.javac.JavaTools
import sbt.io.Path
import sbt.util.Logger
import xsbti.compile.{ScalaInstance => _, _}

import java.io.File
import java.net.URLClassLoader

class CompilerFactoryImpl(sbtData: SbtData) extends CompilerFactory {

  override def createCompiler(compilerData: CompilerData, client: Client, fileToStore: File => AnalysisStore): Compiler = {

    val scalac: Option[AnalyzingCompiler] = getScalac(sbtData, compilerData.compilerJars, client)

    compilerData.incrementalType match {
      case IncrementalityType.SBT =>
        val javac = {
          val scala = getScalaInstance(compilerData.compilerJars)
            .getOrElse(new ScalaInstance(
              version = "stub",
              loader = null,
              loaderCompilerOnly = null,
              loaderLibraryOnly = null,
              libraryJars = Array.empty[File],
              compilerJars = Array.empty[File],
              allJars = Array.empty[File],
              explicitActual = None)
            )
          val classpathOptions = ClasspathOptionsUtil.javac(false)
          JavaTools.directOrFork(scala, classpathOptions, compilerData.javaHome.map(_.toPath))
        }
        new SbtCompiler(javac, scalac, fileToStore)

      case IncrementalityType.IDEA =>
        if (scalac.isDefined) new IdeaIncrementalCompiler(scalac.get)
        else throw new IllegalStateException("Could not create scalac instance")

    }
  }

  private val classloaderCache = Some(new ClassLoaderCache(new URLClassLoader(Array())))

  override def getScalac(sbtData: SbtData, compilerJars: Option[CompilerJars], client: Client): Option[AnalyzingCompiler] = {
    getScalaInstance(compilerJars).map { scalaInstance =>
      val compiledInterfaceJar = getOrCompileInterfaceJar(
        home = sbtData.interfacesHome,
        compilerBridges = sbtData.compilerBridges,
        interfaceJars = Seq(sbtData.sbtInterfaceJar, sbtData.compilerInterfaceJar),
        scalaInstance = scalaInstance,
        javaClassVersion = sbtData.javaClassVersion,
        client = Option(client)
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
    compilerJars.map(getOrCreateScalaInstance)
}

object CompilerFactoryImpl {

  private val scalaInstanceCache = new Cache[CompilerJars, ScalaInstance](3)

  private var classLoadersMap = Map[Seq[File], ClassLoader]()

  def getOrCreateScalaInstance(jars: CompilerJars): ScalaInstance =
    scalaInstanceCache.getOrUpdate(jars)(createScalaInstance(jars))

  private def createScalaInstance(jars: CompilerJars) = {
    def createClassLoader(paths: Seq[File]) = {
      val urls = Path.toURLs(paths)

      // NOTE: it's required for only for Scala3 compilation, cause they moved to `xsbti.CompilerInterface2`
      // We need to use same compile-interface classes in the Scala3 compiler and Scala Compile Server
      // (see commit message for the details  SCL-18861)
      val isXbtiClass = (className: String) => className.startsWith("xsbti.")
      val isNotXbtiClass = (className: String) => !isXbtiClass(className)
      val delegatingToCompilerInterfaceLoader = new classpath.DualLoader(
        classOf[xsbti.AnalysisCallback].getClassLoader, // any class from compiler-interface
        isXbtiClass,
        _ => false,
        ClasspathUtil.rootLoader,
        isNotXbtiClass,
        _ => true
      )

      val newClassloader = new URLClassLoader(urls, delegatingToCompilerInterfaceLoader)

      classLoadersMap += paths -> newClassloader

      newClassloader
    }

    def getOrCreateClassLoader(paths: Seq[File]) = synchronized {
      classLoadersMap.getOrElse(paths, createClassLoader(paths))
    }

    val classLoader = getOrCreateClassLoader(jars.allJars)
    val loaderCompilerOnly = getOrCreateClassLoader(jars.libraryJars ++ jars.compilerJars)
    val loaderLibraryOnly = getOrCreateClassLoader(jars.libraryJars)

    val version = compilerVersion(classLoader)

    new ScalaInstance(
      version = version.getOrElse("unknown"),
      loader = classLoader,
      loaderCompilerOnly,
      loaderLibraryOnly = loaderLibraryOnly,
      libraryJars = jars.libraryJars.toArray,
      compilerJars = jars.compilerJars.toArray,
      allJars = jars.allJars.toArray,
      explicitActual = version
    )
  }

  private def getOrCompileInterfaceJar(home: File,
                                       compilerBridges: SbtData.CompilerBridges,
                                       interfaceJars: Seq[File],
                                       scalaInstance: ScalaInstance,
                                       javaClassVersion: String,
                                       client: Option[Client]): File = {
    val scalaVersion = scalaInstance.actualVersion
    if (is3_0(scalaVersion))
      compilerBridges.scala3._3_0
    else if (isAfter3_1(scalaVersion))
      compilerBridges.scala3._3_1
    else {
      val sourceJar: File =
        if (isBefore_2_11(scalaVersion)) compilerBridges.scala._2_10
        else if (isBefore_2_13(scalaVersion)) compilerBridges.scala._2_11
        else compilerBridges.scala._2_13

      val interfaceId = s"compiler-interface-$scalaVersion-$javaClassVersion"
      val targetJar = new File(home, interfaceId + ".jar")

      if (!targetJar.exists) {
        client.foreach(_.progress(s"Compiling Scalac $scalaVersion interface"))
        home.mkdirs()
        val raw = new RawCompiler(scalaInstance, ClasspathOptionsUtil.auto, NullLogger)
        AnalyzingCompiler.compileSources(
          Seq(sourceJar.toPath),
          targetJar.toPath,
          interfaceJars.map(_.toPath),
          interfaceId,
          raw,
          NullLogger
        )
      }

      targetJar
    }
  }

  private def isBefore_2_11(version: String): Boolean = version.startsWith("2.10") || !version.startsWith("2.1")
  private def isBefore_2_13(version: String): Boolean = version.startsWith("2.11") || version.startsWith("2.12")
  private def is3_0(version: String): Boolean = version.startsWith("3.0")
  private def isAfter3_1(version: String): Boolean = version.startsWith("3.")
}

object NullLogger extends Logger {
  override def log(level: sbt.util.Level.Value, message: => String): Unit = {}

  override def success(message: => String): Unit = {}

  override def trace(t: => Throwable): Unit = {}
}