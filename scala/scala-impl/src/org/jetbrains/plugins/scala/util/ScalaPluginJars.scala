package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import gnu.trove.TByteArrayList
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import org.jetbrains.jps.incremental.BuilderService
import org.jetbrains.plugins.scala.extensions.invokeLater

import java.io.File
import scala.util.parsing.combinator.RegexParsers

object ScalaPluginJars {

  val libRoot: File = {
    val jarPath = new File(PathUtil.getJarPathForClass(this.getClass)) // scalaCommunity.jar
    jarPath.getParentFile
  }

  val jpsRoot: File =
    new File(libRoot, "jps")

  val scalaLibraryJar       = new File(libRoot, "scala-library.jar")
  val scalaReflectJar       = new File(libRoot, "scala-reflect.jar")
  val scalaNailgunRunnerJar = new File(libRoot, "scala-nailgun-runner.jar")
  val compilerSharedJar     = new File(libRoot, "compiler-shared.jar")
  val runnersJarName        = "runners.jar"
  val runnersJar            = new File(libRoot, runnersJarName)
  val replInterface         = new File(libRoot, "repl-interface.jar")

  val nailgunJar             = new File(jpsRoot, "nailgun.jar")
  val sbtInterfaceJar        = new File(jpsRoot, "sbt-interface.jar")
  val incrementalCompilerJar = new File(jpsRoot, "incremental-compiler.jar")
  val compilerJpsJar         = new File(jpsRoot, "compiler-jps.jar")
}


object IntellijPlatformJars {

  val jpsBuildersJar = new File(PathUtil.getJarPathForClass(classOf[BuilderService]))
  val utilJar        = new File(PathUtil.getJarPathForClass(classOf[FileUtil]))
  val trove4jJar     = new File(PathUtil.getJarPathForClass(classOf[TByteArrayList]))
  val fastUtilJar    = new File(PathUtil.getJarPathForClass(classOf[Int2ObjectMap[_]]))

  /**
   * NOTE:<br>
   * There are several protobuf classes in the plugin classpath.<br>
   *  - The required classes (which are used by JPS) are located in "lib/protobuf-java-x.y.z.jar"<br>
   *  - There are also protobuf classes bundled in `layoutlib-27.2.0.0.jar`<br>
   *    The jar is used in the Android plugin, which goes by default in IDEA.<br>
   *
   * We need to ensure that we resolve the right class to avoid issues with communicating with JPS (e.g. SCL-19414).
   */
  val protobufJava: File = {
    val result = new File(PathUtil.getJarPathForClass(classOf[com.google.protobuf.Message]))
    // example in 2021.2: <idea system dir>/lib/protobuf-java-3.15.8.jar
    val Regex = raw"""^.*?/lib/protobuf-java-\d+\.\d+\.\d+\.jar$$""".r
    result.toString.replace("\\", "/").toLowerCase match {
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
  val scalaParserCombinators = new File(PathUtil.getJarPathForClass(classOf[RegexParsers]))
}
