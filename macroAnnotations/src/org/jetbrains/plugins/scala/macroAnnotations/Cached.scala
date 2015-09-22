package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/18/15.
 */
class Cached(synchronized: Boolean = false, modificationCount: ModCount.Value = ModCount.ModificationCount) extends StaticAnnotation {

  def macroTransform(annottees: Any*) = macro CachedMacro.impl
}
