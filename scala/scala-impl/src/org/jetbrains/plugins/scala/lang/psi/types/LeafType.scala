package org.jetbrains.plugins.scala.lang.psi.types


/** Marker trait of all ScTypes with no subtypes (parts) */
trait LeafType {
  this: ScType =>
}
