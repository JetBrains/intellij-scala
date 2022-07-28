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
 */
class CachedWithRecursionGuard(element: Any, defaultValue: => Any, dependecyItem: Object, tracked: Any*) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro CachedWithRecursionGuard.cachedWithRecursionGuardImpl
}

object CachedWithRecursionGuard {
  import CachedMacroUtil._
  def cachedWithRecursionGuardImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._
    implicit val x: c.type = c

    def parameters: (Tree, Tree, Tree, Seq[Tree]) = c.prefix.tree match {
      case q"new CachedWithRecursionGuard(..$params)" if params.length >= 3 =>
        (params.head, params(1), params(2), params.drop(3))
      case _ => abort(MacrosBundle.message("macros.cached.wrong.annotation.parameters"))
    }

    val (element, defaultValue, modTracker, trackedExprs) = parameters

    annottees.toList match {
      case DefDef(mods, termName, tpParams, paramss, retTp, rhs) :: Nil =>
        preventCacheModeParameter(c)(paramss)
        if (retTp.isEmpty) {
          abort(MacrosBundle.message("macros.cached.specify.return.type"))
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
        val name = qualifiedTernName(termName.toString)
        val keyId = stringLiteral(name + "$cacheKey")
        val tracerName = generateTermName(name, "$tracer")
        val cacheName = withClassName(termName)
        val guard = generateTermName(name, "guard")
        val defValueName = generateTermName(name, "defaultValue")
        val elemName = generateTermName(name, "element")
        val dataName = generateTermName(name, "data")
        val keyVarName = generateTermName(name, "key")
        val holderName = generateTermName(name, "holder")
        val dataForGuardName = generateTermName(name, "dataForGuard")

        val dataValue = if (hasParams) q"(..$parameterNames)" else q"()"
        val getOrCreateCachedHolder =
          if (hasParams)
            q"$cachesUtilFQN.getOrCreateCachedMap[$elementType, $dataType, $resultType]($elemName, $keyVarName, $keyId, $cacheName, () => $modTracker)"
          else
            q"$cachesUtilFQN.getOrCreateCachedRef[$elementType, $resultType]($elemName, $keyVarName, $keyId, $cacheName, () => $modTracker)"

        val getFromHolder =
          if (hasParams) q"$holderName.get($dataName)"
          else q"$holderName.get()"

        val updateHolder = new UpdateHolderGenerator[c.type](c) {
          override def apply(resultName: c.universe.TermName): c.universe.Tree =
            if (hasParams) q"$holderName.putIfAbsent($dataName, $resultName)"
            else q"$holderName.compareAndSet(null, $resultName); $holderName.get()"
        }

        val dataForGuardType =
          if (hasParams) tq"($elementType, $dataType)"
          else tq"$elementType"

        val dataForRecursionGuard =
          if (hasParams) q"($elemName, $dataName)"
          else q"$elemName"

        val guardedCalculation = withUIFreezingGuard(c)(rhs, retTp)
        val withProbablyRecursiveException = handleProbablyRecursiveException(c)(elemName, dataName, keyVarName, guardedCalculation)
        val cachedCalculationWithAllTheChecks = doPreventingRecursionCaching(c)(withProbablyRecursiveException, guard, dataForGuardName, updateHolder)

        val updatedRhs = q"""
          val $elemName = $element
          val $dataName = $dataValue
          val $dataForGuardName = $dataForRecursionGuard
          val $keyVarName = ${getOrCreateKey(c, hasParams)(q"$keyId", dataType, resultType)}
          def $defValueName: $resultType = $defaultValue

          val $tracerName = ${internalTracerInstance(c)(keyId, cacheName, trackedExprs)}
          $tracerName.invocation()

          val $holderName = $getOrCreateCachedHolder
          val fromCachedHolder = $getFromHolder
          if (fromCachedHolder != null) return fromCachedHolder

          val $guard = $recursionGuardFQN[$dataForGuardType, $resultType]($keyVarName.toString)
          if ($guard.checkReentrancy($dataForGuardName))
            return $cachesUtilFQN.handleRecursiveCall($elemName, $dataName, $keyVarName, $defValueName)

          $tracerName.calculationStart()

          try {
            $cachedCalculationWithAllTheChecks
          } finally {
            $tracerName.calculationEnd()
          }
          """

        val updatedDef = DefDef(mods, termName, tpParams, paramss, retTp, updatedRhs)

        debug(updatedDef)

        c.Expr(updatedDef)
      case _ => abort(MacrosBundle.message("macros.cached.only.annotate.one.function"))
    }
  }
}
