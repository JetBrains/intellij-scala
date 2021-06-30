package org.jetbrains.plugins.scala.project

import com.intellij.openapi.fileTypes.{FileType, LanguageFileType}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.FileAttribute
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.project.ScalaLanguageLevelPusher.isScalaLike

import scala.annotation.nowarn

/**
 * Used [[com.intellij.openapi.roots.impl.JavaLanguageLevelPusher]] as a reference
 */
class ScalaLanguageLevelPusher extends com.intellij.FileIntPropertyPusher[ScalaLanguageLevel] {

  override def getAttribute: FileAttribute = ScalaLanguageLevelPusher.Persistence

  override def toInt(level: ScalaLanguageLevel): Int = level.ordinal()

  override def fromInt(value: Int): ScalaLanguageLevel = ScalaLanguageLevel.values()(value)

  override def propertyChanged(project: Project, fileOrDir: VirtualFile, actualProperty: ScalaLanguageLevel): Unit = {
    PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(fileOrDir, isScalaLike)
    fileOrDir.getChildren
      .iterator
      .filter(c => !c.isDirectory && isScalaLike(c))
      .foreach { child =>
        PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(child): @nowarn("cat=deprecation")
      }
  }

  override def getFileDataKey: Key[ScalaLanguageLevel] = ScalaLanguageLevel.KEY

  override def pushDirectoriesOnly(): Boolean = true

  override def getDefaultValue: ScalaLanguageLevel = ScalaLanguageLevel.Scala_2_13

  override def getImmediateValue(module: Module): ScalaLanguageLevel = module.scalaLanguageLevel.orNull

  override def getImmediateValue(project: Project, file: VirtualFile): ScalaLanguageLevel = null

  override def acceptsDirectory(file: VirtualFile, project: Project): Boolean =
    ProjectFileIndex.SERVICE.getInstance(project).isInSourceContent(file)
}

object ScalaLanguageLevelPusher {

  private val Persistence = new FileAttribute("scala_language_level_persistence", 1, true)

  @inline
  private def isScalaLike(file: VirtualFile): Boolean =
    isScalaLike(file.getFileType)

  private def isScalaLike(fileType: FileType): Boolean =
    fileType match {
      case lft: LanguageFileType => lft.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
      case _                     => false
    }
}
