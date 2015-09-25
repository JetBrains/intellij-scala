package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/23/15.
 */
class CachedWithRecursionGuard(psiElement: Any, key: Any, defaultValue: => Any, dependencyItem: Object) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro CachedMacro.cachedWithRecursionGuardImpl
}
