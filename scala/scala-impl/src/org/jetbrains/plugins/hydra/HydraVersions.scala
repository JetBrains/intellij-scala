package org.jetbrains.plugins.hydra

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.project.{ProjectExt, ScalaModule, Version}
import com.intellij.openapi.project.Project

import scala.collection.breakOut

/**
  * @author Maris Alexandru
  */
object HydraVersions {

  val DefaultHydraVersion = "0.9.7"
  private val MinScalaVersion = "2.11.8"
  private val UnsupportedScalaVersion = "2.12.0"
  private val CompilerRegex = """.*scala-compiler-(\d+\.\d+\.\d+)(-SNAPSHOT)?\.jar""".r

  private final val Log: Logger = Logger.getInstance(this.getClass.getName)

  def getSupportedScalaVersions(project: Project): Seq[String] = {
    val scalaModules = project.scalaModules
    // we can't use `module.sdk.compilerVersion` because it assumes the *name* of the Sdk library
    // matches `SDK-2.12.3` or the like. For the Scala project this is simply called `starr`, so we
    // need to look inside and retrieve the actual classpath entries
    val scalaVersionsPerModule: Map[ScalaModule, String] = (for {
      module <- scalaModules
      classpathFile <- module.sdk.compilerClasspath
      mtch <- CompilerRegex.findFirstMatchIn(classpathFile.getName)
      scalaVersion = mtch.group(1)
      if scalaVersion != UnsupportedScalaVersion
      version = Version(scalaVersion)
      if version >= Version(MinScalaVersion)
    } yield module -> version.presentation)(breakOut)

    if (scalaModules.size != scalaVersionsPerModule.size) {
      // we have some modules that don't have a scala version, we should log it
      for (module <- scalaModules.filterNot(scalaVersionsPerModule.contains))
        Log.info(s"Could not retrieve Scala version in module '${module.getName}' with compiler classpath: ${module.sdk.compilerClasspath}")
    }

    scalaVersionsPerModule.values.toSeq.distinct
  }
}
