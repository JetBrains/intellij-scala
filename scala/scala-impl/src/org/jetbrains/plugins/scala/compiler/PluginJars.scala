package org.jetbrains.plugins.scala.compiler

import java.io.File

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import gnu.trove.TByteArrayList
import org.jetbrains.jps.incremental.BuilderService

object PluginJars {

  val libRoot: File = {
    if (ApplicationManager.getApplication.isUnitTestMode) {
      new File(System.getProperty("plugin.path"), "lib")
    } else {
      val jarPath = new File(PathUtil.getJarPathForClass(getClass))
      val isDevelopmentMode = jarPath.getName == "classes"
      if (isDevelopmentMode) {
        new File(jarPath.getParentFile, "lib")
      } else  {
        jarPath.getParentFile
      }
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

  val nailgunJar             = new File(jpsRoot, "nailgun.jar")
  val sbtInterfaceJar        = new File(jpsRoot, "sbt-interface.jar")
  val incrementalCompilerJar = new File(jpsRoot, "incremental-compiler.jar")
  val compilerJpsJar         = new File(jpsRoot, "compiler-jps.jar")
}

object PlatformJars {

  val jpsBuildersJar = new File(PathUtil.getJarPathForClass(classOf[BuilderService]))
  val utilJar        = new File(PathUtil.getJarPathForClass(classOf[FileUtil]))
  val trove4jJar     = new File(PathUtil.getJarPathForClass(classOf[TByteArrayList]))
}
