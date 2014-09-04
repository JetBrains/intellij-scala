package org.jetbrains.jps.incremental

import _root_.java.io._
import _root_.java.net.URL
import _root_.java.util.Properties

import _root_.scala.language.implicitConversions

/**
 * @author Pavel Fatin
 */
package object scala {
  type Closeable = {
    def close()
  }

  def using[A <: Closeable, B](resource: A)(block: A => B): B = {
    import _root_.scala.language.reflectiveCalls

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

  implicit def toRightBiasedEither[A, B](either: Either[A, B]): Either.RightProjection[A, B] = either.right

  implicit class PipedObject[T](val v: T) extends AnyVal {
    def |>[R](f: T => R) = f(v)
  }

  def readProperty(classLoader: ClassLoader, resource: String, name: String): Option[String] = {
    Option(classLoader.getResourceAsStream(resource))
      .flatMap(it => using(new BufferedInputStream(it))(readProperty(_, name)))
  }

  def readProperty(file: File, resource: String, name: String): Option[String] = {
    try {
      val url = new URL("jar:%s!/%s".format(file.toURI.toString, resource))
      Option(url.openStream).flatMap(it => using(new BufferedInputStream(it))(readProperty(_, name)))
    } catch {
      case _: IOException => None
    }
  }

  private def readProperty(input: InputStream, name: String): Option[String] = {
    val properties = new Properties()
    properties.load(input)
    Option(properties.getProperty(name))
  }
}
