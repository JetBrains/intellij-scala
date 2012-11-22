package org.jetbrains.jps.incremental

import _root_.java.util.Properties

/**
 * @author Pavel Fatin
 */
package object scala {
  implicit def toRightBiasedEiter[A, B](either: Either[A, B]): Either.RightProjection[A, B] = either.right

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
