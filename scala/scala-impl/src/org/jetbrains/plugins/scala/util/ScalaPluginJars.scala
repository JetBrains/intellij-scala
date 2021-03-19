package org.jetbrains.plugins.scala.util

import com.google.protobuf.GeneratedMessageLite
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import gnu.trove.TByteArrayList
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import org.jetbrains.jps.incremental.BuilderService

import java.io.File
import scala.util.parsing.combinator.RegexParsers

object ScalaPluginJars {

  val libRoot: File = {
    val jarPath = new File(PathUtil.getJarPathForClass(this.getClass)) // scalaUltimate.jar
    // TODO: looks like we do not need this check anymore
    //  looks like dev idea classpath always contains path to jar file
    val isDevelopmentMode = jarPath.getName == "classes"
    if (isDevelopmentMode) {
      new File(jarPath.getParentFile, "lib")
    } else  {
      jarPath.getParentFile
    }
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
  val protobufJava   = new File(PathUtil.getJarPathForClass(classOf[GeneratedMessageLite[_, _]]))
}

object LibraryJars {
  val scalaParserCombinators = new File(PathUtil.getJarPathForClass(classOf[RegexParsers]))
}
