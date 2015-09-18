package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/18/15.
 */
class Cached(synchronized: Boolean = false) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro CachedMacro.impl
}

object CachedMacro {
  def impl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._

    //todo: caching two function with the same name
    //todo: cache with parameters
    annottees.toList match {
      case d@DefDef(mods, name, tpParams, params, retTp, rhs) :: Nil if params.forall(_.isEmpty) =>
        //it's an empty parameter function

        val synchronized: Boolean = c.prefix.tree match {
          case q"new Cached(synchronized = $b)" => c.eval[Boolean](c.Expr(b))
          case q"new Cached($b)" => c.eval[Boolean](c.Expr(b))
          case q"new Cached()" => false
        }

        val cacheVarName = TermName(name.toString + "Cache_")
        val modCountVarName = TermName(name.toString + "ModCount_")

        val functionContents = q"""
            val modCount = getManager.getModificationTracker.getModificationCount
            if ($cacheVarName.isDefined && $modCountVarName == modCount) $cacheVarName.get
            else {
              $cacheVarName = Some(calc())
              $modCountVarName = modCount
              $cacheVarName.get
            }
          """
        val res = q"""

          @volatile
          var $cacheVarName: Option[$retTp] = None

          @volatile
          var $modCountVarName: Long = 0L

          $mods def $name[..$tpParams](): $retTp = {
            def calc(): $retTp = $rhs

            ${ if (synchronized) q"synchronized { $functionContents }" else functionContents }
          }

          """
        println(res)
        c.Expr(res)
      case _ =>
        c.abort(c.enclosingPosition, "You can only annotate one function!")
    }
  }
}