package org.jetbrains.plugins.scala.util

import java.util.concurrent.ConcurrentHashMap

import com.intellij.util.containers.ContainerUtil

import scala.jdk.CollectionConverters._
import scala.collection.mutable

object ScalaCollectionsUtil {

  def newConcurrentMap[K, V]: scala.collection.concurrent.Map[K, V] = {
    new ConcurrentHashMap[K, V]().asScala
  }

  def newConcurrentSet[T]: mutable.Set[T] = {
    ContainerUtil.newConcurrentSet[T]().asScala
  }

}
