package org.jetbrains.sbt.project.modifier.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.{Change, ContentRevision}

class BuildFileChange (val beforeRevision: ContentRevision, val afterRevision: ContentRevision,
                      val buildFileStatus: BuildFileModifiedStatus) extends Change(beforeRevision, afterRevision, buildFileStatus.getChangeStatus) {

  override def getOriginText(project: Project): String = buildFileStatus.getOriginText

}

object BuildFileChange {
  /**
   * Swaps places of before and after revision. This is used to display proper file paths for changed build files since
   * working copies are light virtual files and have no reasonable path associated with them.
   * @param change change to swap revisions for
   */
  def swap(change: BuildFileChange) = new BuildFileChange(change.afterRevision, change.beforeRevision, change.buildFileStatus)
}