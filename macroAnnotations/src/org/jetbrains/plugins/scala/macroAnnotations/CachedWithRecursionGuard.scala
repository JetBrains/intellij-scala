package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
  * This annotation makes the compiler generate code that calls CachesUtil.getWithRecursionPreventingWithRollback
  *
  * NOTE: Annotated function should preferably be top-level or in a static object for better performance.
  * The field for Key is generated and it is more efficient to just keep it stored in this field, rather than get
  * it from CachesUtil every time
  *
  * Author: Svyatoslav Ilinskiy
  * Date: 9/28/15.
  */
class CachedWithRecursionGuard[T](element: Any, defaultValue: => Any, dependecyItem: Object) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro CachedWithRecursionGuard.cachedWithRecursionGuardImpl
}

object CachedWithRecursionGuard {
  import CachedMacroUtil._
  def cachedWithRecursionGuardImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._
    implicit val x: c.type = c

    def parameters: (Tree, Tree, Tree, Tree) = c.prefix.tree match {
      case q"new CachedWithRecursionGuard[$t](..$params)" if params.length == 3 =>
        (params.head, params(1), modCountParamToModTracker(c)(params(2), params.head), t)
      case _ => abort("Wrong annotation parameters!")
    }

    val (element, defaultValue, dependencyItem, providerType) = parameters

    annottees.toList match {
      case DefDef(mods, name, tpParams, params, retTp, rhs) :: Nil =>
        if (retTp.isEmpty) {
          abort("You must specify return type")
        }

        //generated names
        val cachedFunName = TermName(c.freshName("cachedFun"))
        val keyId = c.freshName(name.toString + "cacheKey")
        val keyVarName = TermName(c.freshName(name.toString + "Key"))
        val cacheStatsName = TermName(c.freshName("cacheStats"))
        val analyzeCaches = CachedMacroUtil.analyzeCachesEnabled(c)
        val defdefFQN = q"""getClass.getName ++ "." ++ ${name.toString}"""

        val provider = TypeName("MyProvider")

        val cachedFunRHS = transformRhsToAnalyzeCaches(c)(cacheStatsName, retTp, rhs)

        val fun = q"def $cachedFunName(): $retTp = $cachedFunRHS"
        val builder = q"new $cachesUtilFQN.$provider[$providerType, $retTp]($element, _ => $cachedFunName())($dependencyItem)"

        val updatedRhs = q"""
          ${if (analyzeCaches) q"$cacheStatsName.aboutToEnterCachedArea()" else EmptyTree}
          $fun
          $cachesUtilFQN.incrementModCountForFunsWithModifiedReturn()
          $cachesUtilFQN.getWithRecursionPreventingWithRollback[$providerType, $retTp]($element, $keyVarName, $builder, $defaultValue)
          """
        val updatedDef = DefDef(mods, name, tpParams, params, retTp, updatedRhs)
        val res = q"""
          private val $keyVarName = $cachesUtilFQN.getOrCreateKey[$keyTypeFQN[$cachedValueTypeFQN[$retTp]]]($keyId)

          ${if (analyzeCaches) q"private val $cacheStatsName = $cacheStatisticsFQN($keyId, $defdefFQN)" else EmptyTree}

          ..$updatedDef
          """
        println(res)
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }
}
