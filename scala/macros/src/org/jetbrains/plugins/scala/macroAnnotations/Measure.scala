package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

class Measure extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro Measure.measureImpl
}

object Measure {
  def measureImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import CachedMacroUtil._
    import c.universe._
    implicit val x: c.type = c

    annottees.toList match {
      case DefDef(mods, name, tpParams, paramss, retTp, rhs) :: Nil =>

        val uniqueId = stringLiteral(c.freshName(name))
        val cacheName = stringLiteral(name)
        val updatedBody =
          q"""
            val _tracer_ = $internalTracer($uniqueId, $cacheName)
            _tracer_.invocation()
            _tracer_.calculationStart()
            try {
              $rhs
            } finally _tracer_.calculationEnd()
           """
        val updatedDef = DefDef(mods, name, tpParams, paramss, retTp, updatedBody)

        c.Expr(updatedDef)
      case _ => abort("You can only annotate one function!")
    }
  }
}

