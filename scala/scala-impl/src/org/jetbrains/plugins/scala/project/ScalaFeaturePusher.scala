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
 * Persists the effective [[ScalaFeatures]] for source directories
 * and exposes them to parser/highlighter code through file properties.
 *
 * The pusher stores module-derived feature flags on directories in source content.
 * Consumers can then read those features for a [[com.intellij.psi.PsiFile]] without resolving a module on every access.
 * When the pushed value changes, all Scala-like files in the affected directory are invalidated,
 * so syntax highlighting and parsing are recomputed with the new language feature set.
 *
 * Typical scenarios:
 *  - Scala 3 experimental capture checking should be parsed only when enabled in settings, while still working
 *    for Scala 3.8 standard-library sources loaded from source jars<br>
 *    (see [[https://youtrack.jetbrains.com/issue/SCL-24630 SCL-24630]])
 *  - Raw string unicode-escape behavior differs between Scala 2 and Scala 3,
 *    so lexer/parser consumers must read the pushed feature flags instead of assuming one global behavior<br>
 *    (see [[https://youtrack.jetbrains.com/issue/SCL-18631 SCL-18631]]).
 *
 * @see [[ScalaFeatures]]
 * @see [[com.intellij.openapi.roots.impl.JavaLanguageLevelPusher]]
 */
//noinspection UnstableApiUsage,ApiStatus
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
   * @note if it can't detect features from PsiFile, it fall backs from the features from VirtualFile (if exist).
   *       Q: Though maybe it could do the opposite? First try teh virtual file and if there are not features there, find the file?
   */
  def getFeatures(file: PsiFile): Option[ScalaFeatures] = {
    val containingDir = Option(file.getContainingDirectory)
    val featuresPersistedInContainingDir: Option[ScalaFeatures] =
      containingDir.flatMap(dir => getPersistedFeatures(dir.getVirtualFile))

    def fromIndexedDirOrContainingDir: Option[ScalaFeatures] = {
      val indexedVFile = Option(file.getUserData(IndexingDataKeys.VIRTUAL_FILE))
      // while indexing, the parser will get a dummy file that only references the real file
      val dir = indexedVFile.flatMap(vFile => if (vFile.isDirectory) Some(vFile) else Option(vFile.getParent))
      dir.flatMap(getPersistedFeatures)
    }

    // Original commit: [cc] force scala 3 stdlib source to support capture checking #SCL-24630
    def fromVirtualFile(vFile: VirtualFile): Option[ScalaFeatures] = {
      getPersistedFeatures(vFile).orElse {
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

    val fromPsi = featuresPersistedInContainingDir.orElse(fromIndexedDirOrContainingDir)
    fromPsi.orElse {
      val virtualFile = Option(file.getVirtualFile)
      virtualFile.flatMap(fromVirtualFile)
    }
  }

  private def getPersistedFeatures(dir: VirtualFile): Option[ScalaFeatures] = {
    val persisted = Option(key.getPersistentValue(dir))
    persisted.map(ScalaFeatures.deserializeFromInt(_))
  }

  //TODO: don't use it. Use approach similar to org.jetbrains.plugins.scala.lang.lexer.highlightingLexer.ScalaHighlightingLexerTestBase.configureModuleScalaVersionAndAdditionalCompilerOptions
  // It's closer to the production code
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
