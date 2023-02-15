package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.io.{FileUtil, FileUtilRt}
import com.intellij.util.PathUtil
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
  val scalaJpsJar           = new File(libRoot, "scala-jps.jar")
  val runnersJarName        = "runners.jar"
  val runnersJar            = new File(libRoot, runnersJarName)
  val replInterface         = new File(libRoot, "repl-interface.jar")

  val nailgunJar             = new File(jpsRoot, "nailgun.jar")
  val sbtInterfaceJar        = new File(jpsRoot, "sbt-interface.jar")
  val incrementalCompilerJar = new File(jpsRoot, "incremental-compiler.jar")
  val compileServerJar       = new File(jpsRoot, "compile-server.jar")
  val compilerJpsJar         = new File(jpsRoot, "compiler-jps.jar")
}


object IntellijPlatformJars {

  val jpsBuildersJar = new File(PathUtil.getJarPathForClass(classOf[BuilderService]))
  val utilJar        = new File(PathUtil.getJarPathForClass(classOf[FileUtil]))
  val utilRtJar      = new File(PathUtil.getJarPathForClass(classOf[FileUtilRt]))
  val fastUtilJar    = new File(PathUtil.getJarPathForClass(classOf[Int2ObjectMap[_]]))

  /**
   * NOTE:<br>
   * There are several protobuf classes in the classpath:<br>
   *  - in `idea_system_root/lib/protobuf.jar`
   *  - in `idea_system_root/plugins/java/lib/rt/protobuf-java6.jar` (bundled plugin)
   *  - in `idea_system_root/plugins/android/lib/layoutlib.jar` (bundled plugin)
   *
   * We need to ensure that we resolve the first, which is used by JPSs
   * to avoid runtime errors in communicating with JPS (e.g. SCL-19414).
   *
   * @see [[org.jetbrains.jps.cmdline.ClasspathBootstrap.getBuildProcessApplicationClasspath]]
   */
  val protobufJava: File = {
    val result = new File(PathUtil.getJarPathForClass(classOf[com.google.protobuf.Message]))
    // example in 2021.2: <idea system dir>/lib/protobuf-java-3.15.8.jar
    val Regex = raw"""^.*?/lib/protobuf.jar$$""".r
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
