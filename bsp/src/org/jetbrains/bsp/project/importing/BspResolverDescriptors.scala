package org.jetbrains.bsp.project.importing

import java.io.File

import ch.epfl.scala.bsp4j._
import org.jetbrains.bsp.data.JdkData
import org.jetbrains.bsp.data.{SbtBuildModuleDataBsp, ScalaSdkData}

import scala.util.Try


object BspResolverDescriptors {

  type TestClassId = String

  case class ModuleDescription(data: ModuleDescriptionData,
                               moduleKindData: ModuleKind)

  case class ModuleDescriptionData(id: String,
                                   name: String,
                                   targets: collection.Seq[BuildTarget],
                                   targetDependencies: collection.Seq[BuildTargetIdentifier],
                                   targetTestDependencies: collection.Seq[BuildTargetIdentifier],
                                   basePath: Option[File],
                                   output: Option[File],
                                   testOutput: Option[File],
                                   sourceDirs: collection.Seq[SourceDirectory],
                                   testSourceDirs: collection.Seq[SourceDirectory],
                                   resourceDirs: collection.Seq[SourceDirectory],
                                   testResourceDirs: collection.Seq[SourceDirectory],
                                   classpath: collection.Seq[File],
                                   classpathSources: collection.Seq[File],
                                   testClasspath: collection.Seq[File],
                                   testClasspathSources: collection.Seq[File])

  case class ProjectModules(modules: collection.Seq[ModuleDescription], synthetic: collection.Seq[ModuleDescription])

  sealed abstract class ModuleKind

  case class UnspecifiedModule() extends ModuleKind
  case class JvmModule(jdkData: JdkData) extends ModuleKind
  case class ScalaModule(jdkData: JdkData,
                         scalaSdkData: ScalaSdkData
                        ) extends ModuleKind

  case class SbtModule(jdkData: JdkData,
                       scalaSdkData: ScalaSdkData,
                       sbtData: SbtBuildModuleDataBsp
                      ) extends ModuleKind

  case class TargetData(sources: Try[SourcesResult],
                        dependencySources: Try[DependencySourcesResult],
                        resources: Try[ResourcesResult],
                        scalacOptions: Try[ScalacOptionsResult], // TODO should be optional
                        javacOptions: Try[JavacOptionsResult]
                       )

  case class SourceDirectory(directory: File, generated: Boolean, packagePrefix: Option[String])

}
