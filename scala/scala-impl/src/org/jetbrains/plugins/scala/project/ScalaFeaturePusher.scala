package org.jetbrains.plugins.scala.project

import com.intellij.openapi.fileTypes.{FileType, LanguageFileType}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{FilePropertyKey, FilePropertyKeyImpl, PsiFile}
import com.intellij.util.indexing.IndexingDataKeys
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.{ScalaLanguage, ScalaVersion}
import org.jetbrains.plugins.scala.lang.parser.ScalaLanguageSubstitutor
import org.jetbrains.plugins.scala.project.ScalaFeaturePusher.{SerializedScalaFeatures, isScalaLike}
import org.jetbrains.plugins.scala.project.ScalaFeatures.SerializableScalaFeatures

import scala.annotation.nowarn

/**
 * Used [[com.intellij.openapi.roots.impl.JavaLanguageLevelPusher]] as a reference
 */
class ScalaFeaturePusher extends com.intellij.FilePropertyPusherBase[SerializedScalaFeatures] {

  override def propertyChanged(project: Project, fileOrDir: VirtualFile, actualProperty: SerializedScalaFeatures): Unit = {
    PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(fileOrDir, isScalaLike)
    fileOrDir.getChildren
      .iterator
      .filter(c => !c.isDirectory && isScalaLike(c))
      .foreach { child =>
        PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(child): @nowarn("cat=deprecation")
      }
  }

  override def getFilePropertyKey: FilePropertyKey[SerializedScalaFeatures] = ScalaFeaturePusher.key

  override def pushDirectoriesOnly(): Boolean = true

  override def getDefaultValue: SerializedScalaFeatures = ScalaFeatures.default.serializeToInt

  override def getImmediateValue(module: Module): SerializedScalaFeatures = module.features.serializeToInt

  override def getImmediateValue(project: Project, file: VirtualFile): SerializedScalaFeatures = null

  override def acceptsDirectory(file: VirtualFile, project: Project): Boolean =
    ProjectFileIndex.getInstance(project).isInSourceContent(file)
}

object ScalaFeaturePusher {
  type SerializedScalaFeatures = Integer

  /**
   * @param file can represent a file or a directory
   */
  def getFeatures(file: PsiFile): Option[ScalaFeatures] = {
    val fromContainingDir: Option[ScalaFeatures] =
      Option(file.getContainingDirectory)
        .flatMap(dir => getFeatures(dir.getVirtualFile))

    def fromIndexedDirOrContainingDir: Option[ScalaFeatures] = {
      // while indexing, the parser will get a dummy file that only references the real file
      Option(file.getUserData(IndexingDataKeys.VIRTUAL_FILE))
        .flatMap(vFile => if (vFile.isDirectory) Some(vFile) else Option(vFile.getParent))
        .flatMap(getFeatures)
    }

    // Original commit: [cc] force scala 3 stdlib source to support capture checking #SCL-24630
    def fromVirtualFile(vFile: VirtualFile): Option[ScalaFeatures] = {
      getFeatures(vFile).orElse {
        // Original commit: [cc] parse capture checking only if it is enabled by settings #SCL-24630
        // todo: this is a quick hack for idea253 release
        //       it should be improved by having a comprehensive return value from ScalaLanguageSubstitutor
        val path = vFile.getPath
        val isIn3_8StdLibSource =
          ScalaLanguageSubstitutor.isInSourceJar(path) &&
            ScalaLanguageSubstitutor.looksLikeScala3LibSourcesJar(path)
        Option.when(isIn3_8StdLibSource)(
          ScalaFeatures.onlyByVersion(ScalaVersion.Latest.Scala_3_8)
            .copy(ScalaVersion.Latest.Scala_3_8, hasCaptureCheckingEnabled = true)
        )
      }
    }

    val fromPsi = fromContainingDir.orElse(fromIndexedDirOrContainingDir)
    fromPsi.orElse {
      val virtualFile = Option(file.getVirtualFile)
      virtualFile.flatMap(fromVirtualFile)
    }
  }

  def getFeatures(file: VirtualFile): Option[ScalaFeatures] =
    Option(key.getPersistentValue(file)).map(ScalaFeatures.deserializeFromInt(_))

  @TestOnly
  def setFeatures(dir: VirtualFile, features: SerializableScalaFeatures): Unit =
    key.setPersistentValue(dir, features.serializeToInt)

  @inline
  private def isScalaLike(file: VirtualFile): Boolean =
    isScalaLike(file.getFileType)

  private def isScalaLike(fileType: FileType): Boolean =
    fileType match {
      case lft: LanguageFileType => lft.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
      case _                     => false
    }

  private val key: FilePropertyKey[SerializedScalaFeatures] =
    FilePropertyKeyImpl.createPersistentIntKey(
      "Pushed Scala Features",
      "scala_pushed_feature_persistence",
      ScalaFeatures.version
    )
}
