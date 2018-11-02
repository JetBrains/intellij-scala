package org.jetbrains.jps.incremental.scala.hydra

import java.io.File
import java.net.URLClassLoader

import com.intellij.openapi.diagnostic.{Logger => JpsLogger}
import org.jetbrains.jps.incremental.scala.data.CompilerData
import org.jetbrains.jps.incremental.scala.data.CompilerJars
import org.jetbrains.jps.incremental.scala.data.SbtData
import org.jetbrains.jps.incremental.scala.hydra.data.HydraData
import org.jetbrains.jps.incremental.scala.local.Cache
import org.jetbrains.jps.incremental.scala.local.Compiler
import org.jetbrains.jps.incremental.scala.local.CompilerFactory
import org.jetbrains.jps.incremental.scala.local.CompilerFactoryImpl
import org.jetbrains.jps.incremental.scala.local.IdeaIncrementalCompiler
import org.jetbrains.jps.incremental.scala.local.SbtCompiler
import org.jetbrains.jps.incremental.scala.model.IncrementalityType
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.readProperty
import sbt.internal.inc.AnalyzingCompiler
import sbt.internal.inc.ScalaInstance
import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.internal.inc.javac.JavaTools
import sbt.internal.inc.RawCompiler
import sbt.io.Path
import sbt.util.Logger
import xsbti.compile.AnalysisStore
import xsbti.compile.ClasspathOptionsUtil
import xsbti.compile.ZincCompilerUtil

class HydraCompilerFactory(sbtData: SbtData) extends CompilerFactory {
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

  override def getScalac(sbtData: SbtData, compilerJars: Option[CompilerJars], client: Client): Option[AnalyzingCompiler] = {
    getScalaInstance(compilerJars).map { scala =>
    val compiledInterfaceJar = HydraCompilerFactory.getOrCompileInterfaceJar(sbtData.interfacesHome, sbtData.sourceJars,
      Seq(sbtData.sbtInterfaceJar, sbtData.compilerInterfaceJar), scala, sbtData.javaClassVersion, Option(client))

    new AnalyzingCompiler(scala,
      ZincCompilerUtil.constantBridgeProvider(scala, compiledInterfaceJar),
      ClasspathOptionsUtil.javac(false), _ => (), classloaderCache)
  }
  }

  private def getScalaInstance(compilerJars: Option[CompilerJars]): Option[ScalaInstance] =
    compilerJars.map(HydraCompilerFactory.createScalaInstance)
}

object HydraCompilerFactory {
  private val Log: JpsLogger = JpsLogger.getInstance(CompilerFactoryImpl.getClass.getName)
  private val scalaInstanceCache = new Cache[CompilerJars, ScalaInstance](3)

  var classLoadersMap = Map[Seq[File], ClassLoader]()

  def createScalaInstance(jars: CompilerJars): ScalaInstance = {
    scalaInstanceCache.getOrUpdate(jars) {
      val paths = jars.library +: jars.compiler +: jars.extra

      def createClassLoader() = {
        val urls = Path.toURLs(paths)
        val newClassloader = new URLClassLoader(urls, sbt.internal.inc.classpath.ClasspathUtilities.rootLoader)

        classLoadersMap += paths -> newClassloader

        newClassloader
      }

      val classLoader = synchronized(classLoadersMap.getOrElse(paths, createClassLoader()))

      val version = readScalaVersionIn(classLoader)

      new ScalaInstance(version.getOrElse("unknown"), classLoader, jars.library, jars.compiler, jars.extra.toArray, version)
    }

  }

  def readScalaVersionIn(classLoader: ClassLoader): Option[String] =
    readProperty(classLoader, "compiler.properties", "version.number")

  def getOrCompileInterfaceJar(home: File,
                               sourceJars: SbtData.SourceJars,
                               interfaceJars: Seq[File],
                               scalaInstance: ScalaInstance,
                               javaClassVersion: String,
                               client: Option[Client]): File = {

    val scalaVersion = scalaInstance.actualVersion

    def getSourceJars =
      if (isBefore_2_11(scalaVersion)) sourceJars._2_10
      else if (isBefore_2_13(scalaVersion)) sourceJars._2_11
      else sourceJars._2_13

    val sourceJar =
      if (scalaVersion.contains("hydra")) {
        val hydraBridge = scalaInstance.otherJars().find(_.getName.contains(HydraData.HydraBridgeName))
        if(hydraBridge.isEmpty)
          Log.warn(s"Hydra Bridge was not found for $scalaVersion " +
            s"in ${scalaInstance.otherJars().map(_.getName).mkString(",")}. The vanilla bridge will be used instead.")
        hydraBridge.getOrElse(getSourceJars)
      }
      else getSourceJars

    def getHydraVersion = HydraData.getHydraVersionFromBridge(sourceJar) match {
      case Some(ver) => s"-$ver"
      case _ => ""
    }

    val interfaceId = "compiler-interface-" + scalaVersion + getHydraVersion + "-" + javaClassVersion
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
  override def log(level: sbt.util.Level.Value,message: => String): Unit = {}
  override def success(message: => String): Unit = {}
  override def trace(t: => Throwable): Unit = {}
}