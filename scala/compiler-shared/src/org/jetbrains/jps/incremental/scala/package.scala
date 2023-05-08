package org.jetbrains.jps.incremental

import _root_.java.io._
import _root_.java.net.URL
import _root_.java.util.Properties
import _root_.java.net.URLClassLoader

import _root_.scala.util.Using

package object scala {

  trait Extractor[A, B] extends (A => B) {
    def unapply(a: A): Some[B] = Some(apply(a))
  }

  def containsScala3(files: Iterable[File]): Boolean =
    files.exists(_.getName.startsWith("scala3"))

  // TODO implement a better version comparison
  def compilerVersionIn(compiler: File, versions: String*): Boolean =
    compilerVersion(compiler).exists { version => versions.exists(version.startsWith) }

  def compilerVersion(compiler: File): Option[String] =
    compilerVersion(Set(compiler.toURI.toURL))

  def compilerVersion(urls: Set[URL]): Option[String] =
    compilerVersion(new URLClassLoader(urls.toArray, null))

  def compilerVersion(loader: ClassLoader): Option[String] =
    readProperty(loader, "compiler.properties", "version.number")

  def readProperty(classLoader: ClassLoader, resource: String, name: String): Option[String] = {
    Option(classLoader.getResourceAsStream(resource))
      .flatMap(it => Using.resource(new BufferedInputStream(it))(readProperty(_, name)))
  }

  def readProperty(file: File, resource: String, name: String): Option[String] = {
    try {
      val url = new URL("jar:%s!/%s".format(file.toURI.toString, resource))
      Option(url.openStream).flatMap(it => Using.resource(new BufferedInputStream(it))(readProperty(_, name)))
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
