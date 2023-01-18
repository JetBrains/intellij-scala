package org.jetbrains.plugins.scala.macroAnnotations

import org.jetbrains.annotations.Nls

import scala.annotation.nowarn
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

object CachedMacroUtil {
  val debug: Boolean = false

  def debug(a: Any): Unit = {
    if (debug) {
      Console.println(a)
    }
  }

  private def internalTracer(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.caches.stats.Tracer"
  }

  def internalTracerInstance(c: whitebox.Context)(cacheKey: c.universe.Tree, cacheName: c.universe.Tree, trackedExprs: Seq[c.universe.Tree]): c.universe.Tree = {
    implicit val ctx: c.type = c
    import c.universe.Quasiquote

    if (trackedExprs.nonEmpty) {

      if (!expressionTracersEnabled(c)) {
        val message =
          """Expression tracers are enabled only for debug and tests purposes.
            |Please remove additional expressions from macro annotations""".stripMargin
        abort(message)
      }

      val tracingSuffix = expressionsWithValuesText(c)(trackedExprs)
      val tracingKeyId = q"$cacheKey + $tracingSuffix"
      val tracingKeyName = q"$cacheName + $tracingSuffix"
      q"$internalTracer($tracingKeyId, $tracingKeyName)"
    }
    else q"$internalTracer($cacheKey, $cacheName)"
  }

  private def expressionsWithValuesText(c: whitebox.Context)(trees: Seq[c.universe.Tree]): c.universe.Tree = {
    import c.universe.Quasiquote

    val textTrees = trees.map(p => q"""" " + ${p.toString} + " == " + $p.toString""")
    val concatenation = textTrees match {
      case Seq(t) => q"$t"
      case ts     =>
        def concat(t1: c.universe.Tree, t2: c.universe.Tree) = q"""$t1 + "," + $t2"""
        q"${ts.reduce((t1, t2) => concat(t1, t2))}"
    }

    concatenation
  }

  //expression tracing may have unlimited performance overhead
  //to prevent it's accidental usage in production, compilation will fail on teamcity
  private def expressionTracersEnabled(c: whitebox.Context): Boolean = {
    System.getProperty("ij.scala.compile.server") == "true" || c.settings.contains("enable-expression-tracers")
  }

  def stringLiteral(name: AnyRef)(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"${name.toString}"
  }

  def withClassName(name: AnyRef)(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    @nowarn("cat=deprecation")
    val enclosingName = c.enclosingClass match {
      case df: c.universe.DefTreeApi @unchecked => df.name.toString
      case _ => "________"
    }
    q"${enclosingName + "." + name.toString}"
  }

  @nowarn("cat=deprecation")
  def qualifiedTernName(termName: String)
                       (implicit c: whitebox.Context): String = {
    c.enclosingClass.symbol.fullName.replace('.', '$') + '$' + termName
  }

  def abort(s: String)(implicit c: whitebox.Context): Nothing = c.abort(c.enclosingPosition, s)

  @nowarn("cat=unchecked")
  def box(c: whitebox.Context)(tp: c.universe.Tree): c.universe.Tree = {
    import c.universe.Quasiquote
    tp match {
      case tq"Boolean" => tq"java.lang.Boolean"
      case tq"Int" => tq"java.lang.Integer"
      case tq"Long" => tq"java.lang.Long"
      case _ => tp
    }
  }
}
