package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros

/**
 * This annotation makes the compiler generate code that calls CachesUtil.getMappedWithRecursionPreventingWithRollback
 *
 * Author: Svyatoslav Ilinskiy
 * Date: 9/23/15.
 */
class CachedMappedWithRecursionGuard(psiElement: Any, key: Any, defaultValue: => Any, dependencyItem: Object) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro CachedMacro.cachedMappedWithRecursionGuardImpl
}
