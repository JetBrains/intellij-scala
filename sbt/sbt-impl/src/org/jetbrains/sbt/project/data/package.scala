package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Computable

import java.util
import scala.jdk.CollectionConverters._

package object data {
  private[data] def toJavaSet[A](set: Set[A]): java.util.Set[A] = new util.HashSet[A](set.asJava)
  private[data] def toJavaMap[K, V](map: Map[K, V]): java.util.Map[K, V] = new util.HashMap[K, V](map.asJava)

  private[data] def findModuleForParentOfDataNode[T](dataNode: DataNode[T]): Option[Module] = {
    val parentNode = dataNode.getParent
    if (parentNode != null) Option(parentNode.getUserData(AbstractModuleDataService.MODULE_KEY))
    else None
  }

  private[data] def computeOrphanDataForModuleType(
    moduleType: String,
    projectData: ProjectData,
    modelsProvider: IdeModifiableModelsProvider
  ): Computable[util.Collection[Module]] = {
    () => {
      val orphanIdeModules = new java.util.ArrayList[Module]()

      modelsProvider.getModules.foreach { module =>
        val isPossibleOrphan = !module.isDisposed && ExternalSystemApiUtil.isExternalSystemAwareModule(projectData.getOwner, module) &&
          ExternalSystemApiUtil.getExternalModuleType(module) == moduleType
        if (isPossibleOrphan) {
          val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
          if (projectData.getLinkedExternalProjectPath.equals(rootProjectPath)) {
            if (module.getUserData(AbstractModuleDataService.MODULE_DATA_KEY) == null) {
              orphanIdeModules.add(module)
            }
          }
        }
      }
      orphanIdeModules
    }
  }

}
