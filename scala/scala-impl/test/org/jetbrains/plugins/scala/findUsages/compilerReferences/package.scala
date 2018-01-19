package org.jetbrains.plugins.scala.findUsages

import java.io.InputStream

import scala.reflect.ClassTag

package object utils {
 def loadClass[A](implicit tag: ClassTag[A]): InputStream = {
    val path = tag.runtimeClass.getName.replaceAll("\\.", "/") + ".class"
    getClass.getClassLoader.getResourceAsStream(path)
  }

  def internalName[A](implicit tag: ClassTag[A]): String = tag.runtimeClass.getName.replaceAll("\\.", "/")
  
  def memberOf[A: ClassTag](name: String): String = s"${internalName[A]}.$name"
}
