package org.jetbrains.plugins.hydra

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.{ProjectExt, ScalaModule, ScalaSdk, Version}

/**
  * @author Maris Alexandru
  */
object HydraVersions {

  val DefaultHydraVersion = "0.9.7"
  private val MinScalaVersion = "2.11.8"
  private val UnsupportedScalaVersion = "2.12.0"
  private val CompilerJarName = "scala-compiler"
  private val CompilerVersionRegex = """.*scala-compiler-(\d+\.\d+\.\d+)(-SNAPSHOT)?\.jar""".r

  private final val Log: Logger = Logger.getInstance(this.getClass.getName)

  def getSupportedScalaVersions(project: Project): Seq[String] = {
    val scalaModules = project.scalaModules
    val module2scalaVersion: Map[ScalaModule, String] = (for {
      module <- scalaModules
      scalaVersion <- findScalaVersionInClasspath(module.sdk)
    } yield module -> scalaVersion)(collection.breakOut)

    val (supported, unsupported) = module2scalaVersion.foldLeft(Map.empty[ScalaModule, String] -> Map.empty[ScalaModule, String]) { (acc, e) =>
      val (supported, unsupported) = acc
      val (module, scalaVersion) = e
      if (scalaVersion != UnsupportedScalaVersion && Version(scalaVersion) >= Version(MinScalaVersion))
        (supported + e) -> unsupported
      else
        supported -> (unsupported + e)
    }

    if (unsupported.nonEmpty) {
      // we have some modules that use a Scala version we don't support
      for((module, scalaVersion) <- unsupported)
        Log.info(s"Cannot enable Hydra on module '${module.getName}' because its Scala version ($scalaVersion) is unsupported. The module compiler classpath is: ${module.sdk.compilerClasspath}")
    }

    supported.values.toList.distinct
  }

  private def findScalaVersionInClasspath(sdk: ScalaSdk): Option[String] = {
    // we can't use `module.sdk.compilerVersion` because it assumes the *name* of the Sdk library
    // matches `SDK-2.12.3` or the like. For the Scala project this is simply called `starr`, so we
    // need to look inside and retrieve the actual classpath entries
    val maybeScalaVersion = for {
      compiler <- sdk.compilerClasspath.find(_.getName.contains(CompilerJarName))
      matchedJar <- CompilerVersionRegex.findFirstMatchIn(compiler.getName)
      version <- Option(matchedJar.group(1))
    } yield version

    // However, if the user has set the scalaHome in his sbt build, then the scala-compiler jar in
    // the compiler classpath will likely not have the version appended, and hence the above heuristic
    // for determining the scala version won't work. Therefore, when no version is found, we fallback
    // to using the SDK compilerVersion.
    maybeScalaVersion.orElse(sdk.compilerVersion)
  }
}
