package org.jetbrains.plugins.scala.util

import java.lang.annotation.Annotation
import scala.annotation.tailrec

object Annotations {
  def findAnnotation[T <: Annotation](klass: Class[_], annotationClass: Class[T]): Option[T] = {
    @tailrec
    def inner(c: Class[_]): Annotation = c.getAnnotation(annotationClass) match {
      case null =>
        c.getSuperclass match {
          case null => null
          case parent => inner(parent)
        }
      case annotation => annotation
    }

    Option(inner(klass).asInstanceOf[T])
  }
}
