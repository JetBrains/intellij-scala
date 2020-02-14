package org.jetbrains.jps.incremental.scala.local.worksheet.util

import java.io.InputStream
import java.net.URL
import java.util
import java.util.Collections

/**
 * Hides classes that satisfy `filter` from parent class-loader.
 * Force some classes to be resolved in current classloader, instead of searching in parent.
 *
 * @param parent target parent class loader
 * @param filter returns `true` if the class/package can be resolved in parent,
 *               returns `false` if the class/package should be hidden from parent and resolved in current classloader
 */
final class IsolatingClassLoader(parent: ClassLoader, filter: String => Boolean) extends ClassLoader(parent) {

  override def loadClass(name: String, resolve: Boolean): Class[_] =
    apply(name)(super.loadClass(name, resolve)).orNull

  @deprecated(since = "9")
  override def getPackage(name: String): Package =
    apply(name)(super.getPackage(name)).orNull

  override def getPackages: Array[Package] =
    super.getPackages.filter(p => filter(p.getName))

  override def getResource(name: String): URL =
    apply(name)(super.getResource(name)).orNull

  override def getResourceAsStream(name: String): InputStream =
    apply(name)(super.getResourceAsStream(name)).orNull

  override def getResources(name: String): util.Enumeration[URL] =
    apply(name)(super.getResources(name)).getOrElse(Collections.emptyEnumeration())


  @inline
  private def apply[T](name: String)(body: => T): Option[T] =
    if (filter.apply(name)) {
      Some(body)
    } else {
      None
    }
}

object IsolatingClassLoader {

  def scalaStdLibIsolatingLoader(parent: ClassLoader): IsolatingClassLoader =
    new IsolatingClassLoader(parent, !_.startsWith("scala"))
}