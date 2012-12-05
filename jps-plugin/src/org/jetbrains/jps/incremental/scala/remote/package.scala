package org.jetbrains.jps.incremental.scala

/**
 * @author Pavel Fatin
 */
package object remote {
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
}
