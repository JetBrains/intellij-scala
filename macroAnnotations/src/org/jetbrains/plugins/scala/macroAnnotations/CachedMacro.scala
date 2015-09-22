package org.jetbrains.plugins.scala.macroAnnotations

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/22/15.
 */
object CachedMacro {
  val debug: Boolean = true

  def impl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._

    def abort(message: String) = c.abort(c.enclosingPosition, message)

    def parameters: (Boolean, ModCount.Value) = {
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
    //todo: support recursion guard
    //todo: store data inside the PsiElement?
    //todo: use SoftReferences?
    annottees.toList match {
      case d@DefDef(mods, name, tpParams, params, retTp, rhs) :: Nil =>
        println(retTp)
        if (retTp.toString() == "<type ?>") {
          abort("You must specify return type")
        }

        val (synchronized, modCount) = parameters
        val flattennedParams = params.flatten
        val paramNames = flattennedParams.map(_.name)
        val cacheVarName = TermName(name.toString + "Cache_")
        val modCountVarName = TermName(name.toString + "ModCount_")
        val mapName = TermName(name.toString + "CacheMap")
        val hasParameters: Boolean = flattennedParams.nonEmpty

        val fields = if (hasParameters) {
          q"""
            private val $mapName = com.intellij.util.containers.ContainerUtil.
                newConcurrentMap[(..${flattennedParams.map(_.tpt)}), ($retTp, Long)]()
          """
        } else {
          q"""
            @volatile
            private var $cacheVarName: Option[$retTp] = None
            @volatile
            private var $modCountVarName: Long = 0L
          """
        }

        def getValuesFromMap: c.universe.Tree = q"""
            var ($cacheVarName, $modCountVarName) = Option($mapName.get(..$paramNames)) match {
              case Some((res, count)) => (Some(res), count)
              case _ => (None, 0L)
            }
          """
        def putValuesIntoMap: c.universe.Tree = q"$mapName.put((..$paramNames), ($cacheVarName.get, $modCountVarName))"

        val functionContents = q"""
            val modCount = getManager.getModificationTracker.${TermName(modCount.toString)}
            ..${if (hasParameters) getValuesFromMap else EmptyTree}
            if ($cacheVarName.isDefined && $modCountVarName == modCount) $cacheVarName.get
            else {
              $cacheVarName = Some(calc())
              $modCountVarName = modCount
              ..${if (hasParameters) putValuesIntoMap else EmptyTree}
              $cacheVarName.get
            }
          """
        val res = q"""
          ..$fields
          $mods def $name[..$tpParams](...$params): $retTp = {
            def calc(): $retTp = $rhs

            ..${ if (synchronized) q"synchronized { $functionContents }" else functionContents }
          }
          """
        if (hasParameters) {
          println(res)
        }
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }

  def println(a: Any): Unit = {
    if (debug) {
      Console.println(a)
    }
  }
}

object ModCount extends Enumeration {
  val ModificationCount = Value("getModificationCount")
  val OutOfCodeBlockModificationCount = Value("getOutOfCodeBlockModificationCount")
  val JavaStructureModificationCount = Value("getJavaStructureModificationTracker")
}