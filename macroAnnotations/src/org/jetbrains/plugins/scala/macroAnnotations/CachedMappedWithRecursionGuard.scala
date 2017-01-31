package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
  * This annotation makes the compiler generate code that calls CachesUtil.getMappedWithRecursionPreventingWithRollback
  *
  * NOTE: Annotated function should preferably be top-level or in a static object for better performance.
  * The field for Key is generated and it is more efficient to just keep it stored in this field, rather than get
  * it from CachesUtil every time
  *
  * Author: Svyatoslav Ilinskiy
  * Date: 9/23/15.
  */
class CachedMappedWithRecursionGuard(psiElement: Any, defaultValue: => Any, dependencyItem: Object) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro CachedMappedWithRecursionGuard.cachedMappedWithRecursionGuardImpl
}

object CachedMappedWithRecursionGuard {
  def cachedMappedWithRecursionGuardImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import CachedMacroUtil._
    import c.universe._
    implicit val x: c.type = c

    //generated names
    val dataName: c.universe.TermName = generateTermName("data")

    //noinspection ZeroIndexToHead
    def parameters: (Tree, Tree, Tree) = c.prefix.tree match {
      case q"new CachedMappedWithRecursionGuard(..$params)" if params.length == 3 =>
        (params(0), params(1), modCountParamToModTracker(c)(params(2), params(0)))
      case _ => abort("Wrong annotation parameters!")
    }

    //annotation parameters
    val (element, defaultValue, dependencyItem) = parameters

    annottees.toList match {
      case DefDef(mods, name, tpParams, paramss, retTp, rhs) :: Nil =>
        if (retTp.isEmpty) {
          abort("You must specify return type")
        }

        //some more generated names
        val keyId: String = c.freshName(name + "$keyId")
        val cacheStatsName: c.universe.TermName = generateTermName(name + "cacheStats")
        val defdefFQN = thisFunctionFQN(name.toString)
        val analyzeCaches = analyzeCachesEnabled(c)
        val computedValue = generateTermName("computedValue")
        val guard = generateTermName("guard")
        val defValueName = generateTermName("defaultValue")

        //function parameters
        val flatParams = paramss.flatten
        val parameterTypes = flatParams.map(_.tpt)
        val parameterNames: List[c.universe.TermName] = flatParams.map(_.name)

        val actualCalculation = transformRhsToAnalyzeCaches(c)(cacheStatsName, retTp, rhs)
        val guardedCalculation = withUIFreezingGuard(c)(q"$computedValue = $actualCalculation")

        def handleProbablyRecursiveException(calculation: c.universe.Tree): c.universe.Tree = {
          q"""
              try {
                $calculation
              }
              catch {
                case $cachesUtilFQN.ProbablyRecursionException(`e`, `data`, k, set) if k == key =>
                  try {
                    $calculation
                  } finally {
                    set.foreach(_.setProbablyRecursive(false))
                  }
                case t@$cachesUtilFQN.ProbablyRecursionException(ee, innerData, k, set) if k == key =>
                  val fun = $psiTreeUtilFQN.getContextOfType(e, true, classOf[$scFunctionTypeFqn])
                  if (fun == null || fun.isProbablyRecursive) throw t
                  else {
                    fun.setProbablyRecursive(true)
                    throw t.copy(set = set + fun)
                  }
              }
           """
        }

        val withProbablyRecursiveException = handleProbablyRecursiveException(guardedCalculation)
        val dataForRecursionGuard = q"(e, data)"

        val updatedRhs = q"""
          ${if (analyzeCaches) q"$cacheStatsName.aboutToEnterCachedArea()" else EmptyTree}

          type Dom = $psiElementType
          type Data = (..$parameterTypes)
          type Result = $retTp

          val e: Dom = $element
          val data: Data = (..$parameterNames)
          val key = $cachesUtilFQN.getOrCreateKey[$keyTypeFQN[$cachedValueTypeFQN[$concurrentMapTypeFqn[Data, Result]]]]($keyId)

          def dependencyItem: Object = $dependencyItem
          def $defValueName: Result = $defaultValue

          val map = $cachesUtilFQN.getOrCreateCachedMap[Dom, Data, Result](e, key, () => dependencyItem)

          val fromCache = map.get(data)

          if (fromCache != null) return fromCache

          val $guard = $recursionGuardFQN[(Dom, Data), Result](key.toString)
          if ($guard.currentStackContains($dataForRecursionGuard))
            return $cachesUtilFQN.handleRecursion[Data, Result](e, data, key, $defValueName)

          var $computedValue: $retTp = null

          ${doPreventingRecursion(c)(withProbablyRecursiveException, guard, dataForRecursionGuard, retTp)}

          val result = $computedValue match {
            case null => $defValueName
            case notNull => notNull
          }

          map.put(data, result)
          result
        """

        val cacheStatsField =
          if (analyzeCaches) {
            q"private val $cacheStatsName = $cacheStatisticsFQN($keyId, $defdefFQN)"
          } else EmptyTree

        val updatedDef = DefDef(mods, name, tpParams, paramss, retTp, updatedRhs)
        val res = q"""
          ..$cacheStatsField

          ..$updatedDef
        """
        println(res)
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }
}
