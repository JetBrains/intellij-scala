package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

class TraceWithLogger extends StaticAnnotation {

  def macroTransform(annottees: Any*): Any = macro TraceWithLoggerMacro.impl
}

object TraceWithLoggerMacro {

  def impl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._

    annottees.toList match {
      case DefDef(mods, name, tpParams, paramss, retTp, rhs) :: Nil =>
        val tracingLogger = q"_root_.org.jetbrains.plugins.scala.util.TracingLogger()"

        val methodCallString = CachedMacroUtil.withClassName(name)(c)
        val instanceMarker   = q"""s" (" + (this.hashCode) + ")""""
        val threadMarker     = q"""" (" + __threadName__ + ")""""
        val methodCallMarker = q"""$methodCallString + $instanceMarker + $threadMarker"""

        val updatedBody =
          q"""
            val __start__      = System.nanoTime()
            val __logger__     = $tracingLogger
            val __threadName__ = Thread.currentThread.getName()
            var __exception__  = false

            __logger__.log($methodCallMarker)
            try {
              $rhs
            } catch { case ex: Throwable =>
               __exception__ = true
              throw ex
            } finally {
              val end      = System.nanoTime()
              val tookMs = (end - __start__) / 1000 / 1000
              __logger__.log($methodCallMarker + " (end)" + " (took: " + tookMs + "ms)" + (if ( __exception__ ) " (exception)" else ""))
            }
           """

        val updatedDef = DefDef(mods, name, tpParams, paramss, retTp, updatedBody)
        c.Expr(updatedDef)
      case _                                                        =>
        c.abort(c.enclosingPosition, MacrosBundle.message("macros.cached.only.annotate.one.function"))
    }
  }
}

