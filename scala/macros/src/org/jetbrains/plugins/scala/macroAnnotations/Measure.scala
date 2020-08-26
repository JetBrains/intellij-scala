package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

class Measure(params: Any*) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro Measure.measureImpl
}

object Measure {
  def measureImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import CachedMacroUtil._
    import c.universe._
    implicit val x: c.type = c

    def trackedExprs: Seq[c.universe.Tree] = {
      c.prefix.tree match {
        case q"new Measure(..$params)" => params.map(_.asInstanceOf[c.universe.Tree])
        case _ => Seq.empty
      }
    }

    annottees.toList match {
      case DefDef(mods, termName, tpParams, paramss, retTp, rhs) :: Nil =>

        val freshName = qualifiedTernName(termName.toString)
        val uniqueId = stringLiteral(freshName)
        val cacheName = withClassName(termName)
        val updatedBody =
          q"""
            val _tracer_ = ${internalTracerInstance(c)(uniqueId, cacheName, trackedExprs)}
            _tracer_.invocation()
            _tracer_.calculationStart()
            try {
              $rhs
            } finally _tracer_.calculationEnd()
           """
        val updatedDef = DefDef(mods, termName, tpParams, paramss, retTp, updatedBody)

        c.Expr(updatedDef)
      case _ => abort(MacrosBundle.message("macros.cached.only.annotate.one.function"))
    }
  }
}

