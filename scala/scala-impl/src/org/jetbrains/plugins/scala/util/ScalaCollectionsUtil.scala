package org.jetbrains.plugins.scala.util

import com.intellij.util.containers.ContainerUtil

import scala.collection.JavaConverters._
import scala.collection.mutable

object ScalaCollectionsUtil {

  def newConcurrentMap[K, V]: scala.collection.concurrent.Map[K, V] = {
    ContainerUtil.newConcurrentMap[K, V]().asScala
  }

  def newConcurrentSet[T]: mutable.Set[T] = {
    ContainerUtil.newConcurrentSet[T]().asScala
  }

}
