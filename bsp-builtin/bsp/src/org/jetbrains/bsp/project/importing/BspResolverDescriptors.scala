package org.jetbrains.bsp.project.importing

import ch.epfl.scala.bsp4j._
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.bsp.data.{JdkData, SbtBuildModuleDataBsp, ScalaSdkData}
import org.jetbrains.sbt.project.structure.data.InterpretablePath

import scala.util.Try


object BspResolverDescriptors {

  type TestClassId = String

  case class ModuleDescription(data: ModuleDescriptionData,
                               moduleKindData: ModuleKind) extends Serializable

  case class ModuleDescriptionData(idUri: String,
                                   name: String,
                                   targets: Seq[BuildTarget],
                                   targetDependencies: Seq[BuildTargetIdentifier],
                                   targetTestDependencies: Seq[BuildTargetIdentifier],
                                   basePath: Option[InterpretablePath],
                                   output: Option[InterpretablePath],
                                   testOutput: Option[InterpretablePath],
                                   sourceRoots: Seq[SourceEntry],
                                   testSourceRoots: Seq[SourceEntry],
                                   resourceRoots: Seq[SourceEntry],
                                   testResourceRoots: Seq[SourceEntry],
                                   outputPaths: Seq[InterpretablePath],
                                   classpath: Seq[InterpretablePath],
                                   classpathSources: Seq[InterpretablePath],
                                   testClasspath: Seq[InterpretablePath],
                                   testClasspathSources: Seq[InterpretablePath],
                                   languageLevel: Option[LanguageLevel]) extends Serializable

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

  case class SourceEntry(file: InterpretablePath, isDirectory: Boolean, generated: Boolean, packagePrefix: Option[String]) extends Serializable
}
