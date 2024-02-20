package org.jetbrains.jps.incremental.scala.local

import org.jetbrains.jps.incremental.scala.{Client, CompileServerBundle, compilerVersion}
import org.jetbrains.jps.incremental.scala.local.CompilerFactoryImpl._
import org.jetbrains.jps.incremental.scala.{Client, CompileServerBundle, compilerVersion}
import org.jetbrains.plugins.scala.compiler.data.{CompilerData, CompilerJars, IncrementalityType, SbtData}
import org.jetbrains.plugins.scala.project.Version
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

    val scalacOption: Option[AnalyzingCompiler] = getScalac(sbtData, compilerData.compilerJars, client)

    compilerData.incrementalType match {
      case IncrementalityType.SBT =>
        // Thanks to the Mill maintainers for providing the dummy ScalaInstance workaround.
        // https://github.com/com-lihaoyi/mill/blob/a614c898e485f79de42b514fef3389d04dd96c19/scalalib/worker/src/mill/scalalib/worker/ZincWorkerImpl.scala#L77-L92
        // https://github.com/sbt/zinc/discussions/1263
        // The dummy ScalaInstance is necessary in build modules that do not have a Scala SDK configured, for example,
        // pure Java modules in gradle and Maven.
        def dummyScalaInstance: (ScalaInstance, File) = {
          val dummyFile = new File("")

          val scalaInstance = new ScalaInstance(
            version = "",
            loader = null,
            loaderCompilerOnly = null,
            loaderLibraryOnly = null,
            libraryJars = Array(dummyFile),
            compilerJars = Array(dummyFile),
            allJars = Array.empty,
            explicitActual = Some("")
          )

          (scalaInstance, dummyFile)
        }

        val javac = {
          val scala = compilerData.compilerJars.map(getOrCreateScalaInstance).getOrElse(dummyScalaInstance._1)
          val classpathOptions = ClasspathOptionsUtil.javac(false)
          JavaTools.directOrFork(scala, classpathOptions, compilerData.javaHome.map(_.toPath))
        }

        val scalac = scalacOption.getOrElse {
          val (scalaInstance, dummyFile) = dummyScalaInstance
          val classpathOptions = ClasspathOptions.of(false, false, false, false, false)
          ZincUtil.scalaCompiler(scalaInstance, dummyFile, classpathOptions)
        }

        new SbtCompiler(javac, scalac, fileToStore)

      case IncrementalityType.IDEA =>
        scalacOption match {
          case Some(scalac) => new IdeaIncrementalCompiler(scalac)
          case None => throw new IllegalStateException("Could not create scalac instance")
        }
    }
  }

  private val classloaderCache = Some(new ClassLoaderCache(new URLClassLoader(Array())))

  override def getScalac(sbtData: SbtData, compilerJars: Option[CompilerJars], client: Client): Option[AnalyzingCompiler] = {
    compilerJars.map { compilerJars =>
      val scalaInstance = getOrCreateScalaInstance(compilerJars)
      val customCompilerBridge = compilerJars.customCompilerBridgeJar match {
        case Some(file) if !file.isFile =>
          client.error(CompileServerBundle.message("invalid.compiler.bridge.jar", file))
          None //fallback to bundled bridge
        case other => other
      }

      val compiledInterfaceJar = customCompilerBridge.getOrElse(getOrCompileInterfaceJar(
        home = sbtData.interfacesHome,
        compilerBridges = sbtData.compilerBridges,
        interfaceJars = Seq(sbtData.sbtInterfaceJar, sbtData.compilerInterfaceJar),
        scalaInstance = scalaInstance,
        javaClassVersion = sbtData.javaClassVersion,
        client = Option(client)
      ))

      new AnalyzingCompiler(
        scalaInstance,
        ZincCompilerUtil.constantBridgeProvider(scalaInstance, compiledInterfaceJar),
        ClasspathOptionsUtil.javac(false), _ => (),
        classloaderCache
      )
    }
  }
}

object CompilerFactoryImpl {

  private val scalaInstanceCache = new Cache[CompilerJars, ScalaInstance](3)

  private var classLoadersMap = Map[Seq[File], ClassLoader]()

  private def getOrCreateScalaInstance(jars: CompilerJars): ScalaInstance =
    scalaInstanceCache.getOrUpdate(jars)(() => createScalaInstance(jars))

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
    val scalaVersion = Version(scalaInstance.actualVersion)
    if (is3_0(scalaVersion))
      compilerBridges.scala3._3_0
    else if (is3_1(scalaVersion))
      compilerBridges.scala3._3_1
    else if (is3_2(scalaVersion))
      compilerBridges.scala3._3_2
    else if (is3_3(scalaVersion)) {
      if (scalaVersion.major(3) <= Version("3.3.1")) compilerBridges.scala3._3_3_old
      else compilerBridges.scala3._3_3
    } else if (isLatest3(scalaVersion))
      compilerBridges.scala3._3_4
    else {
      val sourceJar: File =
        if (isBefore_2_11(scalaVersion)) compilerBridges.scala._2_10
        else if (isBefore_2_13(scalaVersion)) compilerBridges.scala._2_11
        else compilerBridges.scala._2_13

      val bridgeFileName = s"compiler-bridge-${scalaVersion.presentation}-$javaClassVersion"
      val targetJar = new File(home, s"$bridgeFileName.jar")

      if (!targetJar.exists) {
        client.foreach(_.progress(CompileServerBundle.message("compiling.scalac.interface", scalaVersion)))
        home.mkdirs()
        val raw = new RawCompiler(scalaInstance, ClasspathOptionsUtil.auto, NullLogger)
        AnalyzingCompiler.compileSources(
          Seq(sourceJar.toPath),
          targetJar.toPath,
          interfaceJars.map(_.toPath),
          id = bridgeFileName,
          raw,
          NullLogger
        )
      }

      targetJar
    }
  }

  private def isBefore_2_11(version: Version): Boolean = version.major(2) < Version("2.11")
  private def isBefore_2_13(version: Version): Boolean = version.major(2) < Version("2.13")
  private def is3_0(version: Version): Boolean = version.presentation.startsWith("3.0")
  private def is3_1(version: Version): Boolean = version.presentation.startsWith("3.1")
  private def is3_2(version: Version): Boolean = version.presentation.startsWith("3.2")
  private def is3_3(version: Version): Boolean = version.presentation.startsWith("3.3")
  private def isLatest3(version: Version): Boolean = version.presentation.startsWith("3.")

  private object NullLogger extends Logger {
    override def log(level: sbt.util.Level.Value, message: => String): Unit = {}

    override def success(message: => String): Unit = {}

    override def trace(t: => Throwable): Unit = {}
  }
}
