package org.jetbrains.plugins.scala
package config

import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService
import java.util
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemApiUtil}

/**
 * @author Pavel Fatin
 */
abstract class AbstractDataService[E, I](key: Key[E]) extends ProjectDataService[E, I] {
  def getTargetDataKey = key

  final def importData(toImport: util.Collection[DataNode[E]], project: Project, synchronous: Boolean) {
    AbstractDataService.invoke(synchronous, project) {
      doImportData(toImport, project)
    }
  }

  def doImportData(toImport: util.Collection[DataNode[E]], project: Project)

  final def removeData(toRemove: util.Collection[_ <: I], project: Project, synchronous: Boolean) {
    AbstractDataService.invoke(synchronous, project) {
      doRemoveData(toRemove, project)
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: I], project: Project)
}

object AbstractDataService {
  def invoke(synchronous: Boolean, manager: ComponentManager)(block: => Unit) {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(manager) {
      def execute() {
        block
      }
    })
  }
}
