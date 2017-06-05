package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
  * This annotation makes the compiler generate code that caches values in the user data of the psiElement.
  * Caches are invalidated on change of `dependencyItem`.
  *
  * Author: Svyatoslav Ilinskiy, Nikolay.Tropin
  * Date: 9/25/15.
  */
class CachedInsidePsiElement(psiElement: Any, dependencyItem: Object) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro CachedInsidePsiElement.cachedInsidePsiElementImpl
}

object CachedInsidePsiElement {
  def cachedInsidePsiElementImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import CachedMacroUtil._
    import c.universe._
    implicit val x: c.type = c
    def parameters: (Tree, Tree) = {
      c.prefix.tree match {
        case q"new CachedInsidePsiElement(..$params)" if params.length == 2 =>
          (params.head, modCountParamToModTracker(c)(params(1), params.head))
        case _ => abort("Wrong annotation parameters!")
      }
    }

    //annotation parameters
    val (elem, dependencyItem) = parameters

    annottees.toList match {
      case DefDef(mods, name, tpParams, paramss, retTp, rhs) :: Nil =>
        if (retTp.isEmpty) {
          abort("You must specify return type")
        }
        //function parameters
        val flatParams = paramss.flatten
        val parameterTypes = flatParams.map(_.tpt)
        val parameterNames: List[c.universe.TermName] = flatParams.map(_.name)
        val hasParams = flatParams.nonEmpty

        //generated types
        val dataType = if (hasParams) tq"(..$parameterTypes)" else tq"Unit"
        val resultType = box(c)(retTp)

        //generated names
        val keyId = c.freshName(name.toString + "cacheKey")
        val cacheStatsName = TermName(c.freshName("cacheStats"))
        val analyzeCaches = CachedMacroUtil.analyzeCachesEnabled(c)
        val defdefFQN = q"""getClass.getName ++ "." ++ ${name.toString}"""
        val elemName = generateTermName("element")
        val dataName = generateTermName("data")
        val keyVarName = generateTermName("key")
        val holderName = generateTermName("holder")
        val resultName = generateTermName("result")
        val cachedFunName = generateTermName(name.toString + "cachedFun")

        val dataValue = if (hasParams) q"(..$parameterNames)" else q"()"
        val getOrCreateCachedHolder =
          if (hasParams)
            q"$cachesUtilFQN.getOrCreateCachedMap[$psiElementType, $dataType, $resultType]($elemName, $keyVarName, () => $dependencyItem)"
          else
            q"$cachesUtilFQN.getOrCreateCachedRef[$psiElementType, $resultType]($elemName, $keyVarName, () => $dependencyItem)"

        val getFromHolder =
          if (hasParams) q"$holderName.get($dataName)"
          else q"$holderName.get()"

        val updateHolder =
          if (hasParams) q"$holderName.putIfAbsent($dataName, $resultName)"
          else q"$holderName.compareAndSet(null, $resultName)"


        val actualCalculation = withUIFreezingGuard(c) {
          transformRhsToAnalyzeCaches(c)(cacheStatsName, retTp, rhs)
        }

        val analyzeCachesEnterCacheArea =
          if (analyzeCaches) q"$cacheStatsName.aboutToEnterCachedArea()"
          else EmptyTree

        val computation = if (hasReturnStatements(c)(actualCalculation)) q"$cachedFunName()" else q"$actualCalculation"

        val updatedRhs = q"""
          def $cachedFunName(): $retTp = $actualCalculation
          ..$analyzeCachesEnterCacheArea

          val $dataName = $dataValue
          val $keyVarName = ${getOrCreateKey(c, hasParams)(q"$keyId", dataType, resultType)}
          val $elemName = $elem

          val $holderName = $getOrCreateCachedHolder
          val fromCachedHolder = $getFromHolder
          if (fromCachedHolder != null) return fromCachedHolder

          val $resultName = $computation
          $updateHolder
          $resultName
          """
        val updatedDef = DefDef(mods, name, tpParams, paramss, retTp, updatedRhs)
        val res = q"""
          ${if (analyzeCaches) q"private val $cacheStatsName = $cacheStatisticsFQN($keyId, $defdefFQN)" else EmptyTree}

          ..$updatedDef
          """
        println(res)
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }
}
