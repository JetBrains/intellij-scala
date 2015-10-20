package org.jetbrains.plugins.scala.macroAnnotations

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
  * Calling this def macro will generate code that clears all maps associated with methodNames passed in
  *
  * NOTE: methodNames passed in should be in the same class and have @Cached annotation
  *
  * Author: Svyatoslav Ilinskiy
  * Date: 10/20/15.
  */
object ClearCache {
  def clearCache(methodNames: String*): Unit = macro clearCacheImpl

  def clearCacheImpl(c: whitebox.Context)(methodNames: c.Expr[String]*): c.Expr[Unit] = {
    import c.universe._

    val clears = methodNames.map { s =>
      val name: String = c.eval[String](c.Expr[String](s.tree))
      q"${TermName(TermName(name) + CachedMacro.cachedMapPostfix)}.clear()"
    }
    val res = q"..$clears"
    CachedMacro.println(res)
    c.Expr(res)
  }
}
