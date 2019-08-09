package org.jetbrains.bsp.project.resolver

import java.io.File

import ch.epfl.scala.bsp4j._
import org.jetbrains.bsp.data.{SbtBuildModuleDataBsp, ScalaSdkData}

import scala.util.Try


private[resolver] object BspResolverDescriptors {

  private[resolver] case class ModuleDescription(data: ModuleDescriptionData,
                                                 moduleKindData: ModuleKind)

  private[resolver] case class ModuleDescriptionData(id: String,
                                                     name: String,
                                                     targets: Seq[BuildTarget],
                                                     targetDependencies: Seq[BuildTargetIdentifier],
                                                     targetTestDependencies: Seq[BuildTargetIdentifier],
                                                     basePath: Option[File],
                                                     output: Option[File],
                                                     testOutput: Option[File],
                                                     sourceDirs: Seq[SourceDirectory],
                                                     testSourceDirs: Seq[SourceDirectory],
                                                     resourceDirs: Seq[SourceDirectory],
                                                     testResourceDirs: Seq[SourceDirectory],
                                                     classpath: Seq[File],
                                                     classpathSources: Seq[File],
                                                     testClasspath: Seq[File],
                                                     testClasspathSources: Seq[File])

  private[resolver] case class ProjectModules(modules: Seq[ModuleDescription], synthetic: Seq[ModuleDescription])

  private[resolver] sealed abstract class ModuleKind

  private[resolver] case class ScalaModule(scalaSdkData: ScalaSdkData) extends ModuleKind
  private[resolver] case class JavaModule() extends ModuleKind
  private[resolver] case class UnspecifiedModule() extends ModuleKind

  private[resolver] case class SbtModule(scalaSdkData: ScalaSdkData,
                                         sbtData: SbtBuildModuleDataBsp
                                        ) extends ModuleKind

  private[resolver] case class TargetData(sources: Try[SourcesResult],
                                          dependencySources: Try[DependencySourcesResult],
                                          resources: Try[ResourcesResult],
                                          scalacOptions: Try[ScalacOptionsResult] // TODO should be optional
                                         )

  private[resolver] case class SourceDirectory(directory: File, generated: Boolean)

}
