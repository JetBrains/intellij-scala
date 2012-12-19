package org.jetbrains.jps.incremental

import _root_.java.util.Properties

/**
 * @author Pavel Fatin
 */
package object scala {
  type Closeable = {
    def close()
  }

  def using[A <: Closeable, B](resource: A)(block: A => B): B = {
    try {
      block(resource)
    } finally {
      resource.close()
    }
  }

  def extractor[A, B](f: A => B) = new Extractor[A, B](f)

  class Extractor[A, B](f: A => B) {
    def unapply(a: A): Some[B] = Some(f(a))
  }

  implicit def toRightBiasedEiter[A, B](either: Either[A, B]): Either.RightProjection[A, B] = either.right

  implicit def toPipedObject[T](value: T) = new {
    def |>[R](f: T => R) = f(value)
  }

  def readProperty(classLoader: ClassLoader, resource: String, name: String): Option[String] = {
    Option(classLoader.getResourceAsStream(resource)).flatMap { stream =>
      try {
        val properties = new Properties()
        properties.load(stream)
        Option(properties.getProperty(name))
      } finally {
        stream.close()
      }
    }
  }
}
