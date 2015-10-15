package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros

/**
 * This annotation makes the compiler generate code that calls CachesUtil.get(..,)
 *
 * Author: Svyatoslav Ilinskiy
 * Date: 9/25/15.
 */
class CachedInsidePsiElement(psiElement: Any, dependencyItem: Object, useOptionalProvider: Boolean = false) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro CachedMacro.cachedInsidePsiElementImpl
}
