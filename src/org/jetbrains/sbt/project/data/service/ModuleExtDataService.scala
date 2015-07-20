package org.jetbrains.sbt.project.data
package service

import java.io.File
import java.util

import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException}
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.project._

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class ModuleExtDataService(val helper: ProjectStructureHelper)
  extends AbstractDataService[ModuleExtData, Library](ModuleExtData.Key)
  with SafeProjectStructureHelper {

  def doImportData(toImport: util.Collection[DataNode[ModuleExtData]], project: Project) =
    toImport.asScala.foreach(doImport(_, project))

  private def doImport(sdkNode: DataNode[ModuleExtData], project: Project): Unit = {
    for {
      module <- getIdeModuleByNode(sdkNode, project)
      data = sdkNode.getData
    } {
      module.configureScalaCompilerSettingsFrom("SBT", data.scalacOptions)
      data.scalaVersion.foreach(version => configureScalaSdk(module, project.scalaLibraries, version, data.scalacClasspath))
      data.jdk.foreach(jdk => configureSdk(module, jdk, data.javacOptions))
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

  private def configureSdk(module: Module, jdk: Sdk, javacOptions: Seq[String]): Unit = {}

  def doRemoveData(toRemove: util.Collection[_ <: Library], project: Project) {}
}
