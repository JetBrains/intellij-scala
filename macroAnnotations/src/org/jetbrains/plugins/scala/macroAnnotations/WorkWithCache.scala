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
object WorkWithCache {
  def workWithCache(operation: String, methodNames: String*): Unit = macro workWithCacheImpl
  def workWithCacheImpl(c: whitebox.Context)(operation: c.Expr[String], methodNames: c.Expr[String]*): c.Expr[Unit] = {
    import c.universe._

    val statements = methodNames.map { s =>
      val name: TermName = TermName(c.eval[String](c.Expr[String](s.tree)))
      val op: TermName = TermName(c.eval[String](c.Expr[String](operation.tree)))
      q"${TermName(name + CachedWithoutModificationCount.cachedMapPostfix)}.$op"
    }
    val res = q"..$statements"
    CachedMacro.println(res)
    c.Expr(res)
  }
}
