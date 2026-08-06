package org.jetbrains.jps.incremental

import _root_.java.io.{BufferedInputStream, IOException, InputStream}
import _root_.java.net.{URL, URLClassLoader}
import _root_.java.nio.file.Path
import _root_.java.util.Properties
import _root_.scala.util.Using

package object scala {

  trait Extractor[A, B] extends (A => B) {
    def unapply(a: A): Some[B] = Some(apply(a))
  }

  def containsScala3(files: Iterable[Path]): Boolean =
    files.exists(_.getFileName.toString.startsWith("scala3"))

  // TODO implement a better version comparison
  def compilerVersionIn(compiler: Path, versions: String*): Boolean =
    compilerVersion(compiler).exists { version => versions.exists(version.startsWith) }

  def compilerVersion(compiler: Path): Option[String] =
    compilerVersion(Set(compiler.toUri.toURL))

  def compilerVersion(urls: Set[URL]): Option[String] =
    compilerVersion(new URLClassLoader(urls.toArray, null))

  def compilerVersion(loader: ClassLoader): Option[String] =
    readProperty(loader, "compiler.properties", "version.number")

  def readProperty(classLoader: ClassLoader, resource: String, name: String): Option[String] = {
    Option(classLoader.getResourceAsStream(resource))
      .flatMap(it => Using.resource(new BufferedInputStream(it))(readProperty(_, name)))
  }

  def readProperty(file: Path, resource: String, name: String): Option[String] = {
    try {
      val url = new URL("jar:%s!/%s".format(file.toUri.toString, resource))
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
