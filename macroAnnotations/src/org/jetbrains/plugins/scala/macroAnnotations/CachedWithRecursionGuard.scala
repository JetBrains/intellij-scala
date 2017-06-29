package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
  * This annotation generates code that caches result of the function. Caches are invalidated on `dependencyItem` change.
  *
  * Computation is also guarded against recursive calls on the same data.
  * See
  * `org.jetbrains.plugins.scala.caches.RecursionManager`
  * `org.jetbrains.plugins.scala.caches.CachesUtil.handleRecursiveCall`
  * `org.jetbrains.plugins.scala.macroAnnotations.CachedMacroUtil.handleProbablyRecursiveException`
  *
  * Author: Svyatoslav Ilinskiy, Nikolay.Tropin
  * Date: 9/28/15.
  */
class CachedWithRecursionGuard(element: Any, defaultValue: => Any, dependecyItem: Object) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro CachedWithRecursionGuard.cachedWithRecursionGuardImpl
}

object CachedWithRecursionGuard {
  import CachedMacroUtil._
  def cachedWithRecursionGuardImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._
    implicit val x: c.type = c

    def parameters: (Tree, Tree, Tree) = c.prefix.tree match {
      case q"new CachedWithRecursionGuard(..$params)" if params.length == 3 =>
        (params.head, params(1), modCountParamToModTracker(c)(params(2), params.head))
      case _ => abort("Wrong annotation parameters!")
    }

    val (element, defaultValue, dependencyItem) = parameters

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
        val elementType = psiElementType

        //generated names
        val keyId = c.freshName(name.toString + "cacheKey")
        val cacheStatsName = TermName(c.freshName("cacheStats"))
        val analyzeCaches = CachedMacroUtil.analyzeCachesEnabled(c)
        val defdefFQN = q"""getClass.getName ++ "." ++ ${name.toString}"""
        val computedValue = generateTermName("computedValue")
        val guard = generateTermName("guard")
        val defValueName = generateTermName("defaultValue")
        val elemName = generateTermName("element")
        val dataName = generateTermName("data")
        val keyVarName = generateTermName("key")
        val holderName = generateTermName("holder")
        val resultName = generateTermName("result")
        val dataForGuardName = generateTermName("dataForGuard")

        val dataValue = if (hasParams) q"(..$parameterNames)" else q"()"
        val getOrCreateCachedHolder =
          if (hasParams)
            q"$cachesUtilFQN.getOrCreateCachedMap[$elementType, $dataType, $resultType]($elemName, $keyVarName, () => $dependencyItem)"
          else
            q"$cachesUtilFQN.getOrCreateCachedRef[$elementType, $resultType]($elemName, $keyVarName, () => $dependencyItem)"

        val getFromHolder =
          if (hasParams) q"$holderName.get($dataName)"
          else q"$holderName.get()"

        val updateHolder =
          if (hasParams) q"$holderName.putIfAbsent($dataName, $resultName)"
          else q"$holderName.compareAndSet(null, $resultName)"

        val dataForGuardType =
          if (hasParams) tq"($elementType, $dataType)"
          else tq"$elementType"

        val dataForRecursionGuard =
          if (hasParams) q"($elemName, $dataName)"
          else q"$elemName"

        val actualCalculation = transformRhsToAnalyzeCaches(c)(cacheStatsName, retTp, rhs)
        val guardedCalculation = withUIFreezingGuard(c)(q"$computedValue = $actualCalculation")
        val withProbablyRecursiveException = handleProbablyRecursiveException(c)(elemName, dataName, keyVarName, guardedCalculation)

        val updatedRhs = q"""
          ${if (analyzeCaches) q"$cacheStatsName.aboutToEnterCachedArea()" else EmptyTree}

          val $elemName = $element
          val $dataName = $dataValue
          val $dataForGuardName = $dataForRecursionGuard
          val $keyVarName = ${getOrCreateKey(c, hasParams)(q"$keyId", dataType, resultType)}
          def $defValueName: $resultType = $defaultValue

          val $holderName = $getOrCreateCachedHolder
          val fromCachedHolder = $getFromHolder
          if (fromCachedHolder != null) return fromCachedHolder

          val $guard = $recursionGuardFQN[$dataForGuardType, $cachedValueProviderResultTypeFQN[$resultType]]($keyVarName.toString)
          if ($guard.currentStackContains($dataForGuardName))
            return $cachesUtilFQN.handleRecursiveCall($elemName, $dataName, $keyVarName, $defValueName)

          var $computedValue: $resultType = null

          ${doPreventingRecursion(c)(withProbablyRecursiveException, guard, dataForGuardName, retTp)}

          val $resultName = $computedValue match {
            case null => $defValueName
            case notNull => notNull
          }

          $updateHolder
          $getFromHolder
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
