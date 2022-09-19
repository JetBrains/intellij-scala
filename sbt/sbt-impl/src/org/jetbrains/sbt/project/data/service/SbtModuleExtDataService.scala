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
import com.intellij.util.CommonProcessors.{CollectProcessor, UniqueProcessor}
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.external._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import java.io.File
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
      SbtModuleExtData(scalaVersion, scalacClasspath, scaladocExtraClasspath, scalacOptions, sdk, javacOptions, packagePrefix, basePackage) = dataNode.getData
    } {
      module.configureScalaCompilerSettingsFrom("sbt", scalacOptions.asScala)
      Option(scalaVersion).foreach(configureScalaSdk(module, _, scalacClasspath.asScala.toSeq, scaladocExtraClasspath.asScala.toSeq)(modelsProvider))
      configureOrInheritSdk(module, Option(sdk))(modelsProvider)
      importJavacOptions(module, javacOptions.asScala.toSeq)(project, modelsProvider)

      val contentEntries = modelsProvider.getModifiableRootModel(module).getContentEntries
      contentEntries.foreach(_.getSourceFolders.foreach(_.setPackagePrefix(Option(packagePrefix).getOrElse(""))))
      ScalaProjectSettings.getInstance(project).setCustomBasePackage(module.getName, basePackage)
    }
  }

  /**
   * Reminder: SbtModuleExtData is built based on `show scalaInstance` sbt command result.
   * In theory looks like if there are no scala libraries in the module, no SbtModuleExtData should be reported for the module
   * But sbt creates `scalaInstance` in such cases anyway
   * see https://github.com/sbt/sbt/issues/6559
   * Also e.g. for Scala 3 (dotty) project, there is not explicit scala3-library dependency in modules,
   * because all modules already depend on scala3-module in the Scala3 project itself
   * So scalaInstance is reported for modules only as compiler which should be used to compile sources
   */
  private def configureScalaSdk(
    module: Module,
    compilerVersion: String,
    scalacClasspath: Seq[File],
    scaladocExtraClasspath: Seq[File]
  )(
    implicit modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    val scalaLibraries = librariesWithScalaRuntimeJar(module)
    if (scalaLibraries.nonEmpty) {
      val scalaLibraryWithSameVersion = scalaLibraries.find(isSameCompileVersionOrLanguageLevel(compilerVersion, _))

      scalaLibraryWithSameVersion match {
        case Some(library) =>
          ScalaSdkUtils.ensureScalaLibraryIsConvertedToScalaSdk(modelsProvider, library, scalacClasspath, scaladocExtraClasspath)
        case None =>
          // example: Scala 3 (dotty) project https://github.com/lampepfl/dotty
          // TODO: dotty modules also have scala-library dependency (scala 2)
          //  The library is reused between modules, and if in some module it's marked as Scala 2 SDK,
          //  it's displayed as SDK in all other modules.
          //  It can be quite confusing.
          //  E.g. ATM in dotty project `tasty-core-scala2` uses Scala 2 and marks scala-library as Scala SDK.
          //  So as a solution, when we convert scala-library to scala sdk we should probably create a copy of it
          //  (which in it's turn might be reused in all modules which depend on the library as on SDK)
          //  see also: org.jetbrains.plugins.scala.project.ScalaModuleSettings SCL-18166, SCL-18867
          createModuleLevelScalaSdk(module, compilerVersion, scalacClasspath, scaladocExtraClasspath)
      }
    }
    else {
      // example: Scala project https://github.com/scala/scala
      createModuleLevelScalaSdk(module, compilerVersion, scalacClasspath, scaladocExtraClasspath)
    }
  }

  private def createModuleLevelScalaSdk(module: Module, compilerVersion: String, scalacClasspath: Seq[File], scaladocExtraClasspath: Seq[File])
                                       (implicit modelsProvider: IdeModifiableModelsProvider): Unit = {
    val rootModel = modelsProvider.getModifiableRootModel(module)
    val testLibrary = rootModel.getModuleLibraryTable.createLibrary(s"scala-sdk-$compilerVersion")
    ScalaSdkUtils.ensureScalaLibraryIsConvertedToScalaSdk(modelsProvider, testLibrary, scalacClasspath, scaladocExtraClasspath)
  }

  private def isSameCompileVersionOrLanguageLevel(compilerVersion: String, scalaLibrary: Library): Boolean =
    scalaLibrary.libraryVersion.exists { version =>
      version == compilerVersion ||
        ScalaLanguageLevel.findByVersion(version) == ScalaLanguageLevel.findByVersion(compilerVersion)
    }

  private def librariesWithScalaRuntimeJar(module: Module)(implicit modelsProvider: IdeModifiableModelsProvider): Iterable[Library] = {
    val delegate = new CollectProcessor[Library] {
      override def accept(library: Library): Boolean = library.hasRuntimeLibrary
    }

    modelsProvider.getModifiableRootModel(module)
      .orderEntries
      .librariesOnly
      .forEachLibrary(new UniqueProcessor[Library](delegate))

    delegate.getResults.asScala
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
