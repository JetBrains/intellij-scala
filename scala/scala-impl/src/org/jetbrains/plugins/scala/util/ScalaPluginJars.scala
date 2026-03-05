package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.io.{FileUtil, FileUtilRt}
import com.intellij.util.PathUtil
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import org.jetbrains.jps.incremental.BuilderService
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.plugins.scala.extensions.{PathExt, invokeLater}

import java.nio.file.Path
import scala.util.parsing.combinator.RegexParsers

object ScalaPluginJars {

  val libRoot: Path = {
    val jarPath = Path.of(PathUtil.getJarPathForClass(this.getClass)) // scalaCommunity.jar
    jarPath.getParent
  }

  val jpsRoot: Path = libRoot / "jps"

  val scalaLibraryJar: Path = libRoot / "scala-library.jar"
  val scala3LibraryJar: Path = libRoot / "scala3-library_3.jar"
  val scalaReflectJar: Path = libRoot / "scala-reflect.jar"
  val scalaNailgunRunnerJar: Path = libRoot / "scala-nailgun-runner.jar"
  val compilerSharedJar: Path = libRoot / "compiler-shared.jar"
  val scalaJpsJar: Path = libRoot / "scala-jps.jar"
  val runnersJarName: String = "runners.jar"
  val runnersJar: Path = libRoot / runnersJarName
  val replInterface: Path = libRoot / "repl-interface.jar"
  val worksheetReplInterfaceImplsJar: Path = libRoot.getParent / "worksheet-repl-interface" / "impls.jar"
  val utilsRt: Path = libRoot / "utils_rt.jar"

  val nailgunJar: Path = jpsRoot / "nailgun.jar"
  val compilerInterfaceJar: Path = jpsRoot / "compiler-interface.jar"
  val sbtInterfaceJar: Path = jpsRoot / "sbt-interface.jar"
  val incrementalCompilerJar: Path = jpsRoot / "incremental-compiler.jar"
  val compileServerJar: Path = jpsRoot / "compile-server.jar"
  val compilerJpsJar: Path = jpsRoot / "compiler-jps.jar"
  val compilerPluginJar_2_12: Path = jpsRoot / "compiler-plugin-2.12.jar"
  val compilerPluginJar_2_13: Path = jpsRoot / "compiler-plugin-2.13.jar"
  val compilerPluginJar_3_3: Path = jpsRoot / "compiler-plugin-3.3.jar"
}

object CompilerBridgeSourcesJars {
  import ScalaPluginJars.jpsRoot

  private def sources(version: String): String = s"compiler-bridge-sources_$version.jar"

  private val compilerBridgeSources_2_10: Path = jpsRoot / sources("2.10")
  private val compilerBridgeSources_2_11: Path = jpsRoot / sources("2.11")
  private val compilerBridgeSources_2_12: Path = jpsRoot / sources("2.12")
  private val compilerBridgeSources_2_13: Path = jpsRoot / sources("2.13")

  val allBridgeSources: Seq[Path] = Seq(
    compilerBridgeSources_2_10,
    compilerBridgeSources_2_11,
    compilerBridgeSources_2_12,
    compilerBridgeSources_2_13
  )
}

object IntellijPlatformJars {

  val jpsBuildersJar: Path = Path.of(PathUtil.getJarPathForClass(classOf[BuilderService]))
  val utilJar: Path = Path.of(PathUtil.getJarPathForClass(classOf[FileUtil]))
  val utilRtJar: Path = Path.of(PathUtil.getJarPathForClass(classOf[FileUtilRt]))
  val fastUtilJar: Path = Path.of(PathUtil.getJarPathForClass(classOf[Int2ObjectMap[_]]))
  val asmJar: Path = Path.of(PathUtil.getJarPathForClass(classOf[ClassReader]))

  /**
   * NOTE:<br>
   * There are several protobuf classes in the classpath:<br>
   *  - in `idea_system_root/lib/protobuf.jar`
   *  - in `idea_system_root/plugins/bazel-plugin/lib/protobuf4.jar`
   *  - in `idea_system_root/plugins/android/lib/layoutlib.jar` (in older versions)
   *  - in `idea_system_root/plugins/java/lib/rt/protobuf-java6.jar` (bundled plugin, in older versions)
   *
   *  (The exact list can vary depending on the exact IntelliJ SDK version)
   *
   * We need to ensure that we resolve the first, which is used by JPSs
   * to avoid runtime errors in communicating with JPS (e.g. SCL-19414).
   *
   * @see [[org.jetbrains.jps.cmdline.ClasspathBootstrap.getBuildProcessApplicationClasspath]]
   */
  val protobufJava: Path = {
    val result = Path.of(PathUtil.getJarPathForClass(classOf[com.google.protobuf.Message]))
    // example in 2021.2: <idea system dir>/lib/protobuf-java-3.15.8.jar
    val Regex = raw"""^.*?/lib/intellij.libraries.protobuf.jar$$""".r
    result.systemIndependentPathString.toLowerCase match {
      case Regex() =>
      case _ =>
        invokeLater {
          throw new AssertionError(s"Unexpected protobuf jar location: $result")
          ()
        }
    }
    result
  }
}

object LibraryJars {
  val scalaParserCombinators: Path = Path.of(PathUtil.getJarPathForClass(classOf[RegexParsers]))
}
