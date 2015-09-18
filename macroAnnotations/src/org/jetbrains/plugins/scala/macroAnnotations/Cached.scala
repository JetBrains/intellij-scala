package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/18/15.
 */
class Cached extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro Cached.impl
}

object Cached {
  def impl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._

    annottees.toList match {
      case d@DefDef(mods, name, tpParams, params, retTp, rhs) :: Nil if params.forall(_.isEmpty) =>
        //it's an empty parameter function
        val cacheVarName = TermName(name.toString + "Cache_")
        val modCountVarName = TermName(name.toString + "ModCount_")
        val res = q"""

          @volatile
          var $cacheVarName: Option[$retTp] = None

          @volatile
          var $modCountVarName: Long = 0L

          $mods def $name[..$tpParams](): $retTp = {
            def calc(): $retTp = $rhs

            val modCount = getManager.getModificationTracker.getModificationCount
            if ($cacheVarName.isDefined && $modCountVarName == modCount) {
              return $cacheVarName.get
            }
            $cacheVarName = Some(calc)
            $modCountVarName = modCount
            return $cacheVarName.get
          }
          """
        c.Expr(res)
      case _ =>
        c.abort(c.enclosingPosition, "You can only annotate one function!")
    }
  }
}