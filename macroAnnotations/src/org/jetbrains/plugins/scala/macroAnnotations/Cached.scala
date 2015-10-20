package org.jetbrains.plugins.scala.macroAnnotations

import org.jetbrains.plugins.scala.macroAnnotations.ModCount.ModCount

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros

/**
  * If you annotate a function with @Cached annotation, the compiler will generate code to cache it.
  *
  * If an annotated function has parameters, one field will be generated (a HashMap).
  * If an annotated function has no parameters, two fields will be generated: result and modCount
  *
  * NOTE !IMPORTANT!: function annotated with @Cached must be on top-most level because generated code generates fields
  * right outside the cached function and if this function is inner it won't work.
  *
  * NOTE: Caching overloaded functions is currently not supported and trying to do that will cause a name collision
  *   and a compile error
  *
  * Author: Svyatoslav Ilinskiy
  * Date: 9/18/15.
  */
class Cached(synchronized: Boolean, modificationCount: ModCount, psiManager: Any) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro CachedMacro.cachedImpl
}
