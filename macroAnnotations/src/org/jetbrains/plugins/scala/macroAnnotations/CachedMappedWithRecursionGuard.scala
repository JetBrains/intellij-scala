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

          type Dom = $psiElementType
          type Data = $dataTypeName
          type Result = $retTp
          val e: Dom = $element
          val data: Data = $dataName
          val key: _root_.com.intellij.openapi.util.Key[_root_.com.intellij.psi.util.CachedValue[_root_.java.util.concurrent.ConcurrentMap[Data, Result]]] = $key
          lazy val defaultValue: Result = $defaultValue
          lazy val dependencyItem: Object = $dependencyItem

          var computed: _root_.com.intellij.psi.util.CachedValue[_root_.java.util.concurrent.ConcurrentMap[Data, Result]] = e.getUserData(key)
          if (computed == null) {
            val manager = _root_.com.intellij.psi.util.CachedValuesManager.getManager(e.getProject)
            computed = manager.createCachedValue(new _root_.com.intellij.psi.util.CachedValueProvider[_root_.java.util.concurrent.ConcurrentMap[Data, Result]] {
              def compute(): _root_.com.intellij.psi.util.CachedValueProvider.Result[_root_.java.util.concurrent.ConcurrentMap[Data, Result]] = {
                new _root_.com.intellij.psi.util.CachedValueProvider.Result(_root_.com.intellij.util.containers.ContainerUtil.newConcurrentMap[Data, Result](), dependencyItem)
              }
            }, false)
            e.putUserData(key, computed)
          }
          val map = computed.getValue
          var result = map.get(data)
          if (result == null) {
            var isCache = true
            result = {
              val guard = $getRecursionGuardFQN(key.toString)
              if (guard.currentStack().contains((e, data))) {
                if (_root_.org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl.isPackageObjectProcessing) {
                  throw new _root_.org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl.DoNotProcessPackageObjectException
                }
                val fun = _root_.com.intellij.psi.util.PsiTreeUtil.getContextOfType(e, true, classOf[_root_.org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction])
                if (fun == null || fun.isProbablyRecursive) {
                  isCache = false
                  defaultValue
                } else {
                  fun.setProbablyRecursive(true)
                  throw new _root_.org.jetbrains.plugins.scala.caches.CachesUtil.ProbablyRecursionException(e, data, key, _root_.scala.collection.immutable.Set(fun))
                }
              } else {
                guard.doPreventingRecursion((e, data), false, new _root_.com.intellij.openapi.util.Computable[_root_.java.lang.Object] {
                  def compute(): _root_.java.lang.Object = {
                    try {
                      val ${generateTermName()}: _root_.scala.Any = e
                      val $dataName: $dataTypeName = data
                      ..$parameterDefinitions

                      if (_root_.org.jetbrains.plugins.scala.util.UIFreezingGuard.isAlreadyGuarded) { $actualCalculation }
                      else _root_.org.jetbrains.plugins.scala.util.UIFreezingGuard.withResponsibleUI { $actualCalculation }
                    }
                    catch {
                      case _root_.org.jetbrains.plugins.scala.caches.CachesUtil.ProbablyRecursionException(`e`, `data`, k, set) if k == key =>
                        try {
                          val ${generateTermName()}: _root_.scala.Any = e
                          val $dataName: $dataTypeName = data
                          ..$parameterDefinitions

                          if (_root_.org.jetbrains.plugins.scala.util.UIFreezingGuard.isAlreadyGuarded) { $actualCalculation }
                          else _root_.org.jetbrains.plugins.scala.util.UIFreezingGuard.withResponsibleUI { $actualCalculation }
                        } finally set.foreach(_.setProbablyRecursive(false))
                      case t@_root_.org.jetbrains.plugins.scala.caches.CachesUtil.ProbablyRecursionException(ee, innerData, k, set) if k == key =>
                        val fun = _root_.com.intellij.psi.util.PsiTreeUtil.getContextOfType(e, true, classOf[_root_.org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction])
                        if (fun == null || fun.isProbablyRecursive) throw t
                        else {
                          fun.setProbablyRecursive(true)
                          throw _root_.org.jetbrains.plugins.scala.caches.CachesUtil.ProbablyRecursionException(ee, innerData, k, set + fun)
                        }
                    }
                  }
                }) match {
                  case null => defaultValue
                  case notNull => notNull.asInstanceOf[Result]
                }
              }
            }
            if (isCache) {
              map.put(data, result)
            }
          }
          result
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
