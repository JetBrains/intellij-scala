package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/18/15.
 */
class Cached(synchronized: Boolean = false, modificationCount: ModCount.Value = ModCount.ModificationCount) extends StaticAnnotation {

  def macroTransform(annottees: Any*) = macro CachedMacro.impl
}

object CachedMacro {
  def impl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._

    def abort(message: String) = c.abort(c.enclosingPosition, message)

    def parameters: (Boolean, ModCount.Value) = {
      val raw: String = showRaw(c.prefix.tree)
      println(raw)
      c.prefix.tree match {
        case q"new Cached(..$params)" =>
          var synch: Option[Boolean] = None
          var modCount: Option[ModCount.Value] = None
          params.foreach {
            case q"synchronized = $b" if synch.isEmpty =>
              synch = Some(c.eval[Boolean](c.Expr(b)))
            case q"modificationCount = ModCount.OutOfCodeBlockModificationCount" if modCount.isEmpty =>
              modCount = Some(ModCount.OutOfCodeBlockModificationCount)
            case q"modificationCount = ModCount.JavaStructureModificationCount" if modCount.isEmpty =>
              modCount = Some(ModCount.JavaStructureModificationCount)
            case q"modificationCount = ModCount.ModificationCount" if modCount.isEmpty=>
              modCount = Some(ModCount.ModificationCount)
            case q"$b" => abort("non-named parameters are not supported yet")
          }
          (synch.getOrElse(false), modCount.getOrElse(ModCount.ModificationCount))
        case q"new Cached()" => (false, ModCount.ModificationCount)
        case _ => abort("Wrong parameters")
      }
    }


    //todo: caching two function with the same name
    //todo: cache with parameters
    annottees.toList match {
      case d@DefDef(mods, name, tpParams, params, retTp, rhs) :: Nil if params.forall(_.isEmpty) =>
        //it's an empty parameter function

        val (synchronized, modCount) = parameters

        val cacheVarName = TermName(name.toString + "Cache_")
        val modCountVarName = TermName(name.toString + "ModCount_")

        val functionContents = q"""
            val modCount = getManager.getModificationTracker.${TermName(modCount.toString)}
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

object ModCount extends Enumeration {
  val ModificationCount = Value("getModificationCount")
  val OutOfCodeBlockModificationCount = Value("getOutOfCodeBlockModificationCount")
  val JavaStructureModificationCount = Value("getJavaStructureModificationTracker")
}