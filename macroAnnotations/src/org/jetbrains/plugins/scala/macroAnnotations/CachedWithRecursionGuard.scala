package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros

/**
 * This annotation makes the compiler generate code that calls CachesUtil.getWithRecursionPreventingWithRollback
 *
 * Author: Svyatoslav Ilinskiy
 * Date: 9/28/15.
 */
class CachedWithRecursionGuard[T](element: Any,
                                   defaultValue: => Any,
                                   dependecyItem: Object,
                                   useOptionalProvider: Boolean = false) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro CachedMacro.cachedWithRecursionGuardImpl
}
