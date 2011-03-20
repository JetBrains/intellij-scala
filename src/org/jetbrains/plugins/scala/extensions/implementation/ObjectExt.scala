package org.jetbrains.plugins.scala.extensions.implementation

/**
 * Pavel Fatin
 */

class ObjectExt[T](v: T) {
  def toOption: Option[T] = if (v == null) None else Some(v)

  def asOptionOf[E: ClassManifest]: Option[E] = if(classManifest[E].erasure.isInstance(v)) Some(v.asInstanceOf[E]) else None

  def getOrElse[H >: T](default: H): H = if (v == null) default else v
}