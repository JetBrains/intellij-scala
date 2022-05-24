package org.jetbrains.sbt.project

import java.util
import java.util.Optional
import scala.jdk.CollectionConverters._

package object data {
  private[data] def toJavaSet[A](set: Set[A]): java.util.Set[A] = new util.HashSet[A](set.asJava)
  private[data] def toJavaMap[K, V](map: Map[K, V]): java.util.Map[K, V] = new util.HashMap[K, V](map.asJava)
}
