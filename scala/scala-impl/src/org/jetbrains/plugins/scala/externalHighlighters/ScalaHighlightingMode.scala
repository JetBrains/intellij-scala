package org.jetbrains.plugins.scala.externalHighlighters

//noinspection ApiStatus,UnstableApiUsage
import com.intellij.ide.impl.TrustedProjects
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.{PsiFile, PsiJavaFile}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScalaFile}
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt, ProjectPsiElementExt}
import org.jetbrains.plugins.scala.settings.{CompilerHighlightingListener, ScalaProjectSettings}

import scala.concurrent.duration._

object ScalaHighlightingMode {

  private def showCompilerErrorsScala2(project: Project): Boolean =
    ScalaProjectSettings.getInstance(project).isCompilerHighlightingScala2
  def showCompilerErrorsScala3(project: Project): Boolean =
    ScalaProjectSettings.getInstance(project).isCompilerHighlightingScala3

  def addSettingsListener(project: Project)
                         (listener: CompilerHighlightingListener): Unit =
    project.getMessageBus
      .connect(project.unloadAwareDisposable)
      .subscribe(CompilerHighlightingListener.Topic, listener)

  //noinspection ApiStatus,UnstableApiUsage
  def isShowErrorsFromCompilerEnabled(project: Project): Boolean =
    TrustedProjects.isTrusted(project) &&
      (showCompilerErrorsScala3(project) && project.hasScala3 ||
        showCompilerErrorsScala2(project) && project.hasScala)

  def isShowErrorsFromCompilerEnabled(file: PsiFile): Boolean =
    file match {
      case scalaFile: ScalaFile => isShowErrorsFromCompilerEnabled(scalaFile)
      case javaFile: PsiJavaFile => isShowErrorsFromCompilerEnabled(javaFile)
      case _ => false
    }

  /**
   * Returns all modules from the project for which compiler based highlighting is enabled. Includes
   * both Scala 2 and Scala 3 modules.
   */
  def compilerBasedHighlightingModules(project: Project): Seq[Module] = {
    val allScalaModules = project.modulesWithScala
    val cbhScala3 = showCompilerErrorsScala3(project)
    val cbhScala2 = showCompilerErrorsScala2(project)
    val scala3 = allScalaModules.filter(cbhScala3 && _.hasScala3)
    val scala2 = allScalaModules.filter(cbhScala2 && _.hasScala)
    scala3 ++ scala2
  }

  private def isShowErrorsFromCompilerEnabled(file: ScalaFile): Boolean = {
    val virtualFile = file match {
      case ScFile.VirtualFile(vFile) => vFile
      case _                         => return false
    }

    val project = file.getProject
    val isRegularScalaFile = virtualFile.getFileType == ScalaFileType.INSTANCE
    if (isRegularScalaFile) {
      file.isScala3File && showCompilerErrorsScala3(project) ||
        file.isScala2File && showCompilerErrorsScala2(project)
    } else if (file.isWorksheetFile) {
      // actually this should work for regular files as well
      file.isInScala3Module && showCompilerErrorsScala3(project) ||
        file.isInScalaModule && showCompilerErrorsScala2(project)
    } else {
      false
    }
  }

  private def isShowErrorsFromCompilerEnabled(javaFile: PsiJavaFile): Boolean =
    showCompilerErrorsScala3(javaFile.getProject) && javaFile.isInScala3Module

  private def nonNegativeDuration(key: String): FiniteDuration =
    Seq(Registry.get(key).asInteger, 0).max.millis

  def compilationDelay: FiniteDuration =
    nonNegativeDuration("scala.highlighting.compilation.delay.millis")
  
  def compilationTimeoutToShowProgress: FiniteDuration =
    nonNegativeDuration("scala.highlighting.compilation.timeout.to.show.progress.millis")

  def perModuleEnabled: Boolean =
    Registry.get("scala.highlighting.compilation.per.module").asBoolean()

  def documentCompilerEnabled: Boolean =
    Registry.get("scala.highlighting.compilation.document.compiler.enabled").asBoolean()
}
