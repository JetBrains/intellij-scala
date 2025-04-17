package org.jetbrains.bsp.project.importing

import ch.epfl.scala.bsp4j._
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.bsp.data.{JdkData, SbtBuildModuleDataBsp, ScalaSdkData}
import org.jetbrains.jps.incremental.scala.remote.SerializablePath

import scala.util.Try


object BspResolverDescriptors {

  type TestClassId = String

  case class ModuleDescription(data: ModuleDescriptionData,
                               moduleKindData: ModuleKind) extends Serializable

  /**
   * @param scalaVersion version of scala compiler used in the target<br>
   *                     Equals to [[org.jetbrains.bsp.data.ScalaSdkData.scalaVersion]] or
   *                     [[ch.epfl.scala.bsp4j.ScalaBuildTarget#getScalaVersion]]
   */
  case class ModuleDescriptionData(idUri: String,
                                   name: String,
                                   targets: Seq[BuildTarget],
                                   targetDependencies: Seq[BuildTargetIdentifier],
                                   targetTestDependencies: Seq[BuildTargetIdentifier],
                                   basePath: Option[SerializablePath],
                                   output: Option[SerializablePath],
                                   testOutput: Option[SerializablePath],
                                   sourceRoots: Seq[SourceEntry],
                                   testSourceRoots: Seq[SourceEntry],
                                   resourceRoots: Seq[SourceEntry],
                                   testResourceRoots: Seq[SourceEntry],
                                   outputPaths: Seq[SerializablePath],
                                   classpath: Seq[SerializablePath],
                                   classpathSources: Seq[SerializablePath],
                                   testClasspath: Seq[SerializablePath],
                                   testClasspathSources: Seq[SerializablePath],
                                   javaLanguageLevel: Option[LanguageLevel],
                                   scalaVersion: Option[String],
                                  ) extends Serializable

  case class ProjectModules(modules: Seq[ModuleDescription], synthetic: Seq[ModuleDescription]) extends Serializable

  sealed abstract class ModuleKind extends Product with Serializable
  object ModuleKind {
    case class UnspecifiedModule() extends ModuleKind

    case class JvmModule(jdkData: JdkData) extends ModuleKind

    case class ScalaModule(
      jdkData: JdkData,
      scalaSdkData: ScalaSdkData
    ) extends ModuleKind

    case class SbtModule(
      jdkData: JdkData,
      scalaSdkData: ScalaSdkData,
      sbtData: SbtBuildModuleDataBsp
    ) extends ModuleKind
  }

  case class TargetData(
    sources: Try[SourcesResult],
    dependencySources: Try[DependencySourcesResult],
    resources: Try[ResourcesResult],
    outputPaths: Try[OutputPathsResult],
    scalacOptions: Try[ScalacOptionsResult], // TODO should be optional
    javacOptions: Try[JavacOptionsResult]
  )

  case class SourceEntry(file: SerializablePath, isDirectory: Boolean, generated: Boolean, packagePrefix: Option[String]) extends Serializable
}
