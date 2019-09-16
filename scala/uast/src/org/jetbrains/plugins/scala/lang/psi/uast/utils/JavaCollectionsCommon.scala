package org.jetbrains.plugins.scala.lang.psi.uast.utils

import java.util

import scala.reflect.ClassTag

object JavaCollectionsCommon {
  def newEmptyJavaList[T] = new util.ArrayList[T](0)

  def newEmptyArray[T: ClassTag] = new Array[T](0)
}
