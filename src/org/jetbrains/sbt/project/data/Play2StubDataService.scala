package org.jetbrains.sbt.project.data

import java.util

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.project.Project

/**
 * User: Dmitry.Naydanov
 * Date: 14.11.14.
 */
class Play2StubDataService extends AbstractDataService[Play2ProjectData, Project](Play2ProjectData.Key) {
  override def doImportData(toImport: util.Collection[DataNode[Play2ProjectData]], project: Project) { }

  override def doRemoveData(toRemove: util.Collection[_ <: Project], project: Project) { }
}
