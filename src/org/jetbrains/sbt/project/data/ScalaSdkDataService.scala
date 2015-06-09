package org.jetbrains.sbt
package project.data

import java.io.File
import java.util
import com.intellij.openapi.externalSystem.model.{ProjectKeys, ExternalSystemException, DataNode}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.externalSystem.service.project.{ProjectStructureHelper, PlatformFacade}
import org.jetbrains.plugins.scala.project._
import collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class ScalaSdkDataService(val helper: ProjectStructureHelper)
  extends AbstractDataService[ScalaSdkData, Library](ScalaSdkData.Key)
  with SafeProjectStructureHelper {

  def doImportData(toImport: util.Collection[DataNode[ScalaSdkData]], project: Project) =
    toImport.asScala.foreach(doImport(_, project))

  private def doImport(sdkNode: DataNode[ScalaSdkData], project: Project): Unit = {
    for {
      module <- getIdeModuleByNode(sdkNode, project)
      sdkData = sdkNode.getData
      compilerOptions = sdkData.compilerOptions
      compilerClasspath = sdkData.compilerClasspath
      compilerVersion = sdkData.scalaVersion
    } {
      module.configureScalaCompilerSettingsFrom("SBT", compilerOptions)
      configureScalaSdk(module, project.scalaLibraries, compilerVersion, compilerClasspath)
    }
  }

  private def configureScalaSdk(module: Module, scalaLibraries: Seq[Library], compilerVersion: Version, compilerClasspath: Seq[File]): Unit =
    if (scalaLibraries.nonEmpty) {
      // TODO Why SBT's scala-libary module version sometimes differs from SBT's declared scalaVersion?
      val scalaLibrary = scalaLibraries
              .find(_.scalaVersion == Some(compilerVersion))
              .orElse(scalaLibraries.find(_.scalaVersion.exists(_.toLanguageLevel == compilerVersion.toLanguageLevel)))
              .getOrElse(throw new ExternalSystemException("Cannot find project Scala library " +
                           compilerVersion.number + " for module " + module.getName))

      if (!scalaLibrary.isScalaSdk)
        scalaLibrary.convertToScalaSdkWith(scalaLibrary.scalaLanguageLevel.getOrElse(ScalaLanguageLevel.Default), compilerClasspath)
    }

  def doRemoveData(toRemove: util.Collection[_ <: Library], project: Project) {}
}
