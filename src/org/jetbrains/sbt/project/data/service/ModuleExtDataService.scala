package org.jetbrains.sbt.project.data
package service

import java.io.File
import java.util

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException}
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.{LanguageLevelModuleExtensionImpl, ModifiableRootModel, ModuleRootManager, ModuleRootModificationUtil}
import com.intellij.util.Consumer
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
      configureOrInheritSdk(module, data.jdk)
      configureLanguageLevel(module, data.javacOptions)
      configureJavacOptions(module, data.javacOptions)
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

  private def configureOrInheritSdk(module: Module, sdk: Option[Sdk]): Unit = {
    ModuleRootModificationUtil.setSdkInherited(module)
    sdk.flatMap(SdkUtils.findProjectSdk).foreach(it => ModuleRootModificationUtil.setModuleSdk(module, it))
  }

  private def configureLanguageLevel(module: Module, javacOptions: Seq[String]): Unit = {
    val moduleSdk = Option(ModuleRootManager.getInstance(module).getSdk)
    val languageLevel = SdkUtils.javaLanguageLevelFrom(javacOptions)
      .orElse(moduleSdk.flatMap(SdkUtils.defaultJavaLanguageLevelIn))
    languageLevel.foreach { level =>
      ModuleRootModificationUtil.updateModel(module, new Consumer[ModifiableRootModel] {
        override def consume(model: ModifiableRootModel): Unit = {
          val extension = model.getModuleExtension(classOf[LanguageLevelModuleExtensionImpl])
          extension.setLanguageLevel(level)
          extension.commit()
        }
      })
    }
  }

  private def configureJavacOptions(module: Module, javacOptions: Seq[String]): Unit = {
    for {
      targetPos <- Option(javacOptions.indexOf("-target")).filterNot(_ == -1)
      targetValue <- javacOptions.lift(targetPos + 1)
      compilerSettings = CompilerConfiguration.getInstance(module.getProject)
    } {
      compilerSettings.setBytecodeTargetLevel(module, targetValue)
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: Library], project: Project) {}
}
