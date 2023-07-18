package org.jetbrains.sbt
package project
package data
package service

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.external._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import java.util
import scala.jdk.CollectionConverters._

final class SbtModuleExtDataService extends ScalaAbstractProjectDataService[SbtModuleExtData, Library](SbtModuleExtData.Key) {

  override def importData(
    toImport: util.Collection[_ <: DataNode[SbtModuleExtData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    val dataToImport = toImport.asScala
    for {
      dataNode <- dataToImport
      module <- modelsProvider.getIdeModuleByNode(dataNode)
      SbtModuleExtData(scalacOptions, sdk, javacOptions, packagePrefix, basePackage, compileOrder) = dataNode.getData
    } {
      module.configureScalaCompilerSettingsFrom("sbt", scalacOptions.asScala, compileOrder)
      configureOrInheritSdk(module, Option(sdk))(modelsProvider)
      importJavacOptions(module, javacOptions.asScala.toSeq)(project, modelsProvider)

      val contentEntries = modelsProvider.getModifiableRootModel(module).getContentEntries
      contentEntries.foreach(_.getSourceFolders.foreach(_.setPackagePrefix(Option(packagePrefix).getOrElse(""))))
      ScalaProjectSettings.getInstance(project).setCustomBasePackage(module.getName, basePackage)
    }
  }

  private def configureOrInheritSdk(module: Module, sdk: Option[SdkReference])(implicit modelsProvider: IdeModifiableModelsProvider): Unit = {
    val model = modelsProvider.getModifiableRootModel(module)
    model.inheritSdk()
    sdk.flatMap(SdkUtils.findProjectSdk).foreach(model.setSdk)
  }

  private def importJavacOptions(module: Module, javacOptions: Seq[String])
                                (implicit project: Project, modelsProvider: IdeModifiableModelsProvider): Unit = {
    configureLanguageLevel(module, javacOptions)
    configureTargetBytecodeLevel(module, javacOptions)
    configureJavacOptions(module, javacOptions)
  }

  private def configureLanguageLevel(module: Module, javacOptions: Seq[String])
                                    (implicit project: Project, modelsProvider: IdeModifiableModelsProvider): Unit = executeProjectChangeAction {
    val model = modelsProvider.getModifiableRootModel(module)
    val moduleSdk = Option(model.getSdk)
    val languageLevelFromJavac = JavacOptionsUtils.javaLanguageLevel(javacOptions)
    val languageLevel = languageLevelFromJavac.orElse(moduleSdk.flatMap(SdkUtils.defaultJavaLanguageLevelIn))
    languageLevel.foreach { level =>
      val extension = model.getModuleExtension(classOf[LanguageLevelModuleExtensionImpl])
      extension.setLanguageLevel(level)
    }
  }

  private def configureTargetBytecodeLevel(module: Module, javacOptions: Seq[String])
                                          (implicit project: Project): Unit = executeProjectChangeAction {
    val targetValueFromJavac = JavacOptionsUtils.effectiveTargetValue(javacOptions)
    val compilerSettings = CompilerConfiguration.getInstance(module.getProject)
    compilerSettings.setBytecodeTargetLevel(module, targetValueFromJavac.orNull)
  }

  private def configureJavacOptions(module: Module, javacOptions0: Seq[String])(implicit project: Project): Unit = {
    val javacOptions = JavacOptionsUtils.withoutExplicitlyHandledOptions(javacOptions0)

    val compilerSettings = CompilerConfiguration.getInstance(module.getProject)

    val moduleCurrentOptions0 = compilerSettings.getAdditionalOptions(module).asScala.toSeq
    val moduleCurrentOptions: Seq[String] = {
      val moduleCurrentStr = moduleCurrentOptions0.mkString(" ")
      val projectCurrentStr = JavacConfiguration.getOptions(project, classOf[JavacConfiguration]).ADDITIONAL_OPTIONS_STRING

      // NOTE: getAdditionalOptions fallbacks to project options if module options are empty
      // so we assume if they are equal then current module additional options are empty
      if (moduleCurrentStr == projectCurrentStr) Nil
      else moduleCurrentOptions0
    }

    if (javacOptions != moduleCurrentOptions) {
      executeProjectChangeAction {
        compilerSettings.setAdditionalOptions(module, javacOptions.asJava)
      }
    }
  }

}
