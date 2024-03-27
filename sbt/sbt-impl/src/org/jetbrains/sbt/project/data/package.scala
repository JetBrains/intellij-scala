package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.module.Module

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
}
