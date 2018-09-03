package org.jetbrains.sbt.resolvers.indexes

import java.io.File

import com.intellij.openapi.application.{ApplicationManager, PathManager}
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.sbt._
import org.jetbrains.sbt.resolvers.ArtifactInfo

/**
  * @author Mikhail Mutcianko
  * @since 26.07.16
  */
trait ResolverIndex {
  def searchGroup(artifactId: String = ""): Set[String]

  def searchArtifact(groupId: String = ""): Set[String]

  def searchVersion(groupId: String, artifactId: String): Set[String]

  def searchArtifactInfo(fqName: String)
                        (implicit project: ProjectContext): Set[ArtifactInfo]

  def doUpdate(progressIndicator: Option[ProgressIndicator] = None): Unit

  def getUpdateTimeStamp: Long

  def close(): Unit
}

object ResolverIndex {
  val DEFAULT_INDEXES_DIR: File = new File(PathManager.getSystemPath) / "sbt" / "indexes"
  val CURRENT_INDEX_VERSION = "6"
  val NO_TIMESTAMP: Int = -1
  val MAVEN_UNAVALIABLE: Int = -2
  val FORCE_UPDATE_KEY = "ivy.index.force.update" // disable index building in tests for performance reasons, use this to override
  def getIndexDirectory(root: String) = new File(indexesDir, root.shaDigest)

  val indexesDir: File = {
    if (ApplicationManager.getApplication.isUnitTestMode)
      Option(System.getProperty("ivy.test.indexes.dir"))
        .map(new File(_))
        .getOrElse(DEFAULT_INDEXES_DIR)
    else DEFAULT_INDEXES_DIR
  }

  object Paths {
    val PROPERTIES_FILE = "index.properties"
    val ARTIFACT_TO_GROUP_FILE = "artifact-to-group.map"
    val GROUP_TO_ARTIFACT_FILE = "group-to-artifact.map"
    val GROUP_ARTIFACT_TO_VERSION_FILE = "group-artifact-to-version.map"
    val FQ_NAME_TO_GROUP_ARTIFACT_VERSION_FILE = "fqname-to-group-artifact-version.map"
  }

  object Keys {
    val VERSION = "version"
    val ROOT = "root"
    val UPDATE_TIMESTAMP = "update-timestamp"
    val KIND = "kind"
  }

}

