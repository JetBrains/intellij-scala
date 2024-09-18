package org.jetbrains.bsp.project.importing

import java.io.File

import ch.epfl.scala.bsp4j._
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.bsp.data.JdkData
import org.jetbrains.bsp.data.{SbtBuildModuleDataBsp, ScalaSdkData}

import scala.util.Try


object BspResolverDescriptors {

  type TestClassId = String

  case class ModuleDescription(data: ModuleDescriptionData,
                               moduleKindData: ModuleKind)

  case class ModuleDescriptionData(idUri: String,
                                   name: String,
                                   targets: Seq[BuildTarget],
                                   targetDependencies: Seq[BuildTargetIdentifier],
                                   targetTestDependencies: Seq[BuildTargetIdentifier],
                                   basePath: Option[File],
                                   output: Option[File],
                                   testOutput: Option[File],
                                   sourceRoots: Seq[SourceEntry],
                                   testSourceRoots: Seq[SourceEntry],
                                   resourceRoots: Seq[SourceEntry],
                                   testResourceRoots: Seq[SourceEntry],
                                   outputPaths: Seq[File],
                                   classpath: Seq[File],
                                   classpathSources: Seq[File],
                                   testClasspath: Seq[File],
                                   testClasspathSources: Seq[File],
                                   languageLevel: Option[LanguageLevel])

  case class ProjectModules(modules: Seq[ModuleDescription], synthetic: Seq[ModuleDescription])

  sealed abstract class ModuleKind
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

  case class SourceEntry(file: File, isDirectory: Boolean, generated: Boolean, packagePrefix: Option[String])
}
