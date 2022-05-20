package org.jetbrains.plugins.scala.util

import com.intellij.util.containers.ContainerUtil

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.jdk.CollectionConverters._

object ScalaCollectionsUtil {

  def newConcurrentMap[K, V]: scala.collection.concurrent.Map[K, V] = {
    new ConcurrentHashMap[K, V]().asScala
  }

  def newConcurrentSet[T]: mutable.Set[T] = {
    ContainerUtil.newConcurrentSet[T]().asScala
  }

}
