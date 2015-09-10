package org.jetbrains.sbt.project.data
package service

import java.io.File
import java.util

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationCategory, NotificationData, NotificationSource}
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.{LanguageLevelModuleExtensionImpl, ModifiableRootModel, ModuleRootManager, ModuleRootModificationUtil}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.project._
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.SbtProjectSystem

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
      data.scalaVersion.foreach(version => configureScalaSdk(project, module, version, data.scalacClasspath))
      configureOrInheritSdk(module, data.jdk)
      configureLanguageLevel(module, data.javacOptions)
      configureJavacOptions(module, data.javacOptions)
    }
  }

  private def configureScalaSdk(project: Project, module: Module, compilerVersion: Version, compilerClasspath: Seq[File]): Unit = {
    val scalaLibraries = module.scalaLibraries
    if (scalaLibraries.nonEmpty) {
      val scalaLibrary = scalaLibraries
        .find(_.scalaVersion.contains(compilerVersion))
        .orElse(scalaLibraries.find(_.scalaVersion.exists(_.toLanguageLevel == compilerVersion.toLanguageLevel)))

      scalaLibrary match {
        case Some(library) if !library.isScalaSdk =>
          library.convertToScalaSdkWith(library.scalaLanguageLevel.getOrElse(ScalaLanguageLevel.Default), compilerClasspath)
        case None =>
          showWarning(project, SbtBundle("sbt.dataService.scalaLibraryIsNotFound", compilerVersion.number, module.getName))
        case _ => // do nothing
      }
    }
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

  private def showWarning(project: Project, message: String): Unit = {
    val notification = new NotificationData(SbtBundle("sbt.notificationGroupTitle"), message, NotificationCategory.WARNING, NotificationSource.PROJECT_SYNC)
    notification.setBalloonGroup(SbtBundle("sbt.notificationGroupName"))
    ExternalSystemNotificationManager.getInstance(project).showNotification(SbtProjectSystem.Id, notification)
  }

  def doRemoveData(toRemove: util.Collection[_ <: Library], project: Project) {}
}
