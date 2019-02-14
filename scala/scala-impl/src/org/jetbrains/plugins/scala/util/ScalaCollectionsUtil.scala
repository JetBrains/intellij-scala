package org.jetbrains.plugins.scala.util

import com.intellij.util.containers.ContainerUtil

object ScalaCollectionsUtil {

  def newConcurrentMap[K, V]: scala.collection.concurrent.Map[K, V] = {
    val jMap = ContainerUtil.newConcurrentMap[K, V]()
    collection.convert.Wrappers.JConcurrentMapWrapper(jMap)
  }

}
