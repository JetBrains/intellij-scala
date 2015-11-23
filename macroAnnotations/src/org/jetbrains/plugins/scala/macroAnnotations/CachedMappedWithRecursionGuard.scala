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
    val cachedFunName: c.universe.TermName = generateTermName("cachedFun")
    val dataName: c.universe.TermName = generateTermName("data")
    val dataTypeName: c.universe.TypeName = generateTypeName("Data")

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
        val keyId: String = c.freshName(name + "cacheKey")
        val key: c.universe.TermName = generateTermName(name + "Key")
        val cacheStatsName: c.universe.TermName = generateTermName(name + "cacheStats")
        val defdefFQN = thisFunctionFQN(name.toString)
        val analyzeCaches = analyzeCachesEnabled(c)

        //function parameters
        val flatParams = paramss.flatten
        val parameterTypes = flatParams.map(_.tpt)
        val parameterNames: List[c.universe.TermName] = flatParams.map(_.name)
        val parameterDefinitions: List[c.universe.Tree] = flatParams match {
          case param :: Nil => List(ValDef(NoMods, param.name, param.tpt, q"$dataName"))
          case _ => flatParams.zipWithIndex.map {
            case (param, i) => ValDef(NoMods, param.name, param.tpt, q"$dataName.${TermName("_" + (i + 1))}")
          }
        }

        val actualCalculation = transformRhsToAnalyzeCaches(c)(cacheStatsName, retTp, rhs)
        val builder = q"""
          def $cachedFunName(${generateTermName()}: _root_.scala.Any, $dataName: $dataTypeName): $retTp = {
            ..$parameterDefinitions

            $actualCalculation
          }
          """

        val updatedRhs = q"""
          ${if (analyzeCaches) q"$cacheStatsName.aboutToEnterCachedArea()" else EmptyTree}
          type $dataTypeName = (..$parameterTypes)
          val $dataName = (..$parameterNames)

          $builder

          $getMappedWithRecursionFQN[$psiElementType, $dataTypeName, $retTp]($element, $dataName, $key,
            $cachedFunName, $defaultValue, $dependencyItem)
        """

        val cacheStatsField =
          if (analyzeCaches) {
            q"private val $cacheStatsName = $cacheStatisticsFQN($keyId, $defdefFQN)"
          } else EmptyTree

        val updatedDef = DefDef(mods, name, tpParams, paramss, retTp, updatedRhs)
        val res = q"""
          private val $key = $cachesUtilFQN.getOrCreateKey[$mappedKeyTypeFQN[(..$parameterTypes), $retTp]]($keyId)
          ..$cacheStatsField

          ..$updatedDef
        """
        println(res)
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }
}
