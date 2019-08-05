package org.jetbrains.jps.incremental.scala.local.worksheet.util

/**
 * Hides classes that satisfy `filter` from parent class-loader.
 * Force some classes to be resolved in current classloader, instead of searching in parent.
 *
 * @param parent target parent class loader
 * @param filter returns `true` if the class can be resolved in parent,
 *               returns `false` if the class should be hidden from parent and resolved in current classloader
 */
final class IsolatingClassLoader(parent: ClassLoader, filter: String => Boolean) extends ClassLoader(parent) {

  override def loadClass(className: String, resolve: Boolean): Class[_] =
    if (filter.apply(className)) {
      super.loadClass(className, resolve)
    } else {
      null
    }
}

object IsolatingClassLoader {

  def scalaStdLibIsolatingLoader(parent: ClassLoader): IsolatingClassLoader =
    new IsolatingClassLoader(this.getClass.getClassLoader, !_.startsWith("scala"))
}