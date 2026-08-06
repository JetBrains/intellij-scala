package org.jetbrains.jps.incremental.scala.local

import org.jetbrains.jps.incremental.scala.local.CompilerFactoryImpl._
import org.jetbrains.jps.incremental.scala.{Client, CompileServerBundle, compilerVersion}
import org.jetbrains.plugins.scala.compiler.data.{CompilerData, CompilerJars, IncrementalityType, SbtData}
import org.jetbrains.plugins.scala.project.Version
import sbt.internal.inc._
import sbt.internal.inc.classpath.{ClassLoaderCache, ClasspathUtil}
import sbt.internal.inc.javac.JavaTools
import sbt.util.Logger
import xsbti.compile.{ScalaInstance => _, _}

import java.net.URLClassLoader
import java.nio.file.{Files, Path, Paths}

class CompilerFactoryImpl(sbtData: SbtData) extends CompilerFactory {

  override def createCompiler(compilerData: CompilerData, client: Client, fileToStore: Path => AnalysisStore): Compiler = {

    val scalacOption: Option[AnalyzingCompiler] = getScalac(sbtData, compilerData.compilerJars, client)

    compilerData.incrementalType match {
      case IncrementalityType.SBT =>
        // Thanks to the Mill maintainers for providing the dummy ScalaInstance workaround.
        // https://github.com/com-lihaoyi/mill/blob/a614c898e485f79de42b514fef3389d04dd96c19/scalalib/worker/src/mill/scalalib/worker/ZincWorkerImpl.scala#L77-L92
        // https://github.com/sbt/zinc/discussions/1263
        // The dummy ScalaInstance is necessary in build modules that do not have a Scala SDK configured, for example,
        // pure Java modules in gradle and Maven.
        def dummyScalaInstance: (ScalaInstance, Path) = {
          val dummyFile = Paths.get("")

          val scalaInstance = new ScalaInstance(
            version = "",
            loader = null,
            loaderCompilerOnly = null,
            loaderLibraryOnly = null,
            libraryJars = Array(dummyFile.toFile),
            compilerJars = Array(dummyFile.toFile),
            allJars = Array.empty,
            explicitActual = Some("")
          )

          (scalaInstance, dummyFile)
        }

        val javac = {
          val scala = compilerData.compilerJars.map(getOrCreateScalaInstance).getOrElse(dummyScalaInstance._1)
          val classpathOptions = ClasspathOptionsUtil.javac(false)
          JavaTools.directOrFork(scala, classpathOptions, compilerData.javaHome)
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
        case Some(file) if !Files.isRegularFile(file) =>
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
        ZincCompilerUtil.constantBridgeProvider(scalaInstance, compiledInterfaceJar.toFile),
        ClasspathOptionsUtil.javac(false), _ => (),
        classloaderCache
      )
    }
  }
}

object CompilerFactoryImpl {

  import com.intellij.openapi.diagnostic.{Logger => IdeaLogger}

  private val Log: IdeaLogger = IdeaLogger.getInstance(classOf[CompilerFactoryImpl])

  private val scalaInstanceCache = new Cache[CompilerJars, ScalaInstance](3)

  private var classLoadersMap = Map[Seq[Path], ClassLoader]()

  private def getOrCreateScalaInstance(jars: CompilerJars): ScalaInstance =
    scalaInstanceCache.getOrUpdate(jars)(() => createScalaInstance(jars))

  private def createScalaInstance(jars: CompilerJars) = {
    def createClassLoader(paths: Seq[Path]) = {
      val urls = paths.map(_.toUri.toURL).toArray

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

    def getOrCreateClassLoader(paths: Seq[Path]) = synchronized {
      classLoadersMap.getOrElse(paths, createClassLoader(paths))
    }

    val classLoader = getOrCreateClassLoader(jars.allJars)
    val loaderCompilerOnly = getOrCreateClassLoader((jars.libraryJars ++ jars.compilerJars))
    val loaderLibraryOnly = getOrCreateClassLoader(jars.libraryJars)

    val version = compilerVersion(classLoader)

    new ScalaInstance(
      version = version.getOrElse("unknown"),
      loader = classLoader,
      loaderCompilerOnly,
      loaderLibraryOnly = loaderLibraryOnly,
      libraryJars = jars.libraryJars.map(_.toFile).toArray,
      compilerJars = jars.compilerJars.map(_.toFile).toArray,
      allJars = jars.allJars.map(_.toFile).toArray,
      explicitActual = version
    )
  }

  private def getOrCompileInterfaceJar(home: Path,
                                       compilerBridges: SbtData.CompilerBridges,
                                       interfaceJars: Seq[Path],
                                       scalaInstance: ScalaInstance,
                                       javaClassVersion: String,
                                       client: Option[Client]): Path = {
    val scalaVersion = Version(scalaInstance.actualVersion)
    if (is3_0(scalaVersion))
      compilerBridges.scala3._3_0
    else if (is3_1(scalaVersion))
      compilerBridges.scala3._3_1
    else if (is3_2(scalaVersion))
      compilerBridges.scala3._3_2
    else if (is3_3(scalaVersion)) {
      if (scalaVersion.major(3) <= Version("3.3.1")) compilerBridges.scala3._3_3_1
      else compilerBridges.scala3._3_3
    } else if (isLatest3(scalaVersion))
      compilerBridges.scala3._3_4
    else {
      val sourceJar: Path =
        if (isBefore_2_11(scalaVersion)) compilerBridges.scala._2_10
        else if (is_2_11(scalaVersion)) compilerBridges.scala._2_11
        else if (isBeforeOrExactly_2_12_0(scalaVersion)) compilerBridges.scala._2_11
        else if (is_2_12(scalaVersion)) compilerBridges.scala._2_12
        else if (isExactly_2_13_0_M1(scalaVersion)) compilerBridges.scala._2_12
        else compilerBridges.scala._2_13

      val bridgeFileName = s"compiler-bridge-${scalaVersion.presentation}-$javaClassVersion.jar"
      val targetJar = home.resolve(bridgeFileName)

      if (!Files.exists(targetJar)) {
        client.foreach(_.progress(CompileServerBundle.message("compiling.scalac.interface", scalaVersion)))
        Files.createDirectories(home)
        val raw = new RawCompiler(scalaInstance, ClasspathOptionsUtil.auto, NullLogger)
        Log.info(s"Compiling compiler bridge sources $sourceJar for Scala $scalaVersion to: $targetJar")
        AnalyzingCompiler.compileSources(
          Seq(sourceJar),
          targetJar,
          interfaceJars,
          id = bridgeFileName,
          raw,
          NullLogger
        )
      }

      Log.info(s"Compiler bridge for Scala $scalaVersion: $targetJar")
      targetJar
    }
  }

  private def isBefore_2_11(version: Version): Boolean = version.major(2) < Version("2.11")
  private def is_2_11(version: Version): Boolean = version.major(2) == Version("2.11")
  private def isBeforeOrExactly_2_12_0(version: Version): Boolean = version <= Version("2.12.0")
  private def is_2_12(version: Version): Boolean = version.major(2) == Version("2.12")
  private def isExactly_2_13_0_M1(version: Version): Boolean = version == Version("2.13.0-M1")
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
