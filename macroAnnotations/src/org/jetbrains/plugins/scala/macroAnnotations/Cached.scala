package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros

/**
 * If you annotate a function with @Cached annotation, the compiler will generate code to cache it.
 *
 * If an annotated function has parameters, one field will be generated (a HashMap).
 * If an annotated function has no parameters, two fields will be generated: result and modCount
 *
 * CURRENTLY NOT SUPPORTED:
 *   1. Caching overloaded functions in the same scope will cause a name collision
 *   2. Passing non-named arguments to this annotation. For instance this will cause a compiler error:
 *      `
 *        &#064;Cached(false, ModCount.ModificationCount)
 *        def foo(): Int = ???
 *      `
 *
 *      You have to do the following:
 *      `
 *        &#064;Cached(synchronized = false, modificationCount = ModCount.ModificationCount)
 *        def foo(): Int = ???
 *      `
 *
 * Author: Svyatoslav Ilinskiy
 * Date: 9/18/15.
 */
class Cached(synchronized: Boolean = false, modificationCount: ModCount.Value = ModCount.ModificationCount) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro CachedMacro.cachedImpl
}
