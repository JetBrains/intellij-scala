package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros

/**
  * This annotation makes the compiler generate code that calls CachesUtil.getMappedWithRecursionPreventingWithRollback
  *
  * NOTE: Annotated function should preferably be top-level or in a static object for better performance.
  * The field for Key is generated and it is more efficient to just keep it stored in this field, rather than get
  * it from CachesUtil every time
  *
  * Author: Svyatoslav Ilinskiy
  * Date: 9/23/15.
  */
class CachedMappedWithRecursionGuard(psiElement: Any, defaultValue: => Any, dependencyItem: Object) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro CachedMacro.cachedMappedWithRecursionGuardImpl
}
