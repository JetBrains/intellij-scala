package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.tailrec
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/22/15.
 */
object CachedMacro {
  val debug: Boolean = false
  //to analyze caches pass in the following compiler flag: "-Xmacro-settings:analyze-caches"
  val ANALYZE_CACHES: String = "analyze-caches"


  def cachedImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._

    //fully qualified names
    val cacheStatisticsFQN = q"_root_.org.jetbrains.plugins.scala.statistics.CacheStatistics"

    def abort(message: String) = c.abort(c.enclosingPosition, message)

    def parameters: (Boolean, ModCount.Value, Tree) = {
      @tailrec
      def modCountParam(modCount: c.universe.Tree): ModCount.Value = modCount match {
        case q"modificationCount = $v" => modCountParam(v)
        case q"ModCount.$v" => ModCount.withName(v.toString)
        case q"$v" => ModCount.withName(v.toString)
      }

      c.prefix.tree match {
        case q"new Cached(..$params)" if params.length == 3 =>
          val synch: Boolean = params.head match {
            case q"synchronized = $v" => c.eval[Boolean](c.Expr(v))
            case q"$v" => c.eval[Boolean](c.Expr(v))
          }
          val modCount: ModCount.Value = modCountParam(params(1))
          val manager = params(2)
          (synch, modCount, manager)
        case _ => abort("Wrong parameters")
      }
    }

    //annotation parameters
    val (synchronized, modCount, manager) = parameters

    annottees.toList match {
      case DefDef(mods, name, tpParams, paramss, retTp, rhs) :: Nil =>
        if (retTp.isEmpty) {
          abort("You must specify return type")
        }
        //generated names
        val cacheVarName = c.freshName(name)
        val modCountVarName = c.freshName(name)
        val mapName = c.freshName(name)
        val cachedFunName = TermName(c.freshName("cachedFun"))
        val cacheStatsName = TermName(c.freshName(name + "cacheStats"))
        val keyId = c.freshName(name.toString + "cacheKey")
        val analyzeCaches = c.settings.contains(ANALYZE_CACHES)

        //DefDef parameters
        val flatParams = paramss.flatten
        val paramNames = flatParams.map(_.name)
        val hasParameters: Boolean = flatParams.nonEmpty

        val analyzeCachesField =
          if(analyzeCaches) {
            val cacheDecl = q"private val $cacheStatsName = $cacheStatisticsFQN($keyId, ${name.toString})"
            if (hasParameters) { //need to put map in cacheStats, so its size can be measured
              q"""
                $cacheDecl

                $cacheStatsName.addCacheObject($mapName)
              """
            } else cacheDecl
          } else EmptyTree
        val fields = if (hasParameters) {
          q"""
            private val $mapName = _root_.com.intellij.util.containers.ContainerUtil.
                newConcurrentMap[(..${flatParams.map(_.tpt)}), ($retTp, _root_.scala.Long)]()

            ..$analyzeCachesField
          """
        } else {
          q"""
            new _root_.scala.volatile()
            private var $cacheVarName: _root_.scala.Option[$retTp] = _root_.scala.None
            new _root_.scala.volatile()
            private var $modCountVarName: _root_.scala.Long = 0L

            ..$analyzeCachesField
          """
        }

        def getValuesFromMap: c.universe.Tree = q"""
            var ($cacheVarName, $modCountVarName) = _root_.scala.Option($mapName.get(..$paramNames)) match {
              case _root_.scala.Some((res, count)) => (_root_.scala.Some(res), count)
              case _ => (_root_.scala.None, 0L)
            }
          """
        def putValuesIntoMap: c.universe.Tree = q"$mapName.put((..$paramNames), ($cacheVarName.get, $modCountVarName))"

        val analyzeCachesBeforeCalculation =
          if (analyzeCaches) {
            q"""
              if ($cacheVarName.isDefined) $cacheStatsName.removeCacheObject($cacheVarName.get)
              val startTime = System.nanoTime()
            """
          } else EmptyTree

        val analyzeCachesAfterCalculation =
          if (analyzeCaches) {
            q"""
              val stopTime = System.nanoTime()
              $cacheStatsName.reportTimeToCalculate(stopTime - startTime)
              $cacheStatsName.addCacheObject($cacheVarName)
            """
          } else EmptyTree

        val functionContents = q"""
            ${if (analyzeCaches) q"$cacheStatsName.aboutToEnterCachedArea()" else EmptyTree}
            val modCount = $manager.getModificationTracker.${TermName(modCount.toString)}
            ..${if (hasParameters) getValuesFromMap else EmptyTree}
            if ($cacheVarName.isEmpty || $modCountVarName != modCount) {
              ..$analyzeCachesBeforeCalculation
              val cacheFunResult = $cachedFunName()
              ..$analyzeCachesAfterCalculation
              $cacheVarName = _root_.scala.Some(cacheFunResult)
              $modCountVarName = modCount
              ..${if (hasParameters) putValuesIntoMap else EmptyTree}
            }
            $cacheVarName.get
          """
        val updatedRhs = q"""
          def $cachedFunName(): $retTp = {
            ${if (analyzeCaches) q"$cacheStatsName.recalculatingCache()" else EmptyTree}
            $rhs
          }
          ..${ if (synchronized) q"synchronized { $functionContents }" else functionContents }
        """
        val updatedDef = DefDef(mods, name, tpParams, paramss, retTp, updatedRhs)
        val res = q"""
          ..$fields
          $updatedDef
          """
        println(res)
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }

  def cachedMappedWithRecursionGuardImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._

    //fully qualified names
    val cachesUtilFQN = q"_root_.org.jetbrains.plugins.scala.caches.CachesUtil"
    val mappedKeyTypeFQN = tq"_root_.org.jetbrains.plugins.scala.caches.CachesUtil.MappedKey"
    val methodFQN = q"$cachesUtilFQN.getMappedWithRecursionPreventingWithRollback"
    val cacheStatisticsFQN = q"_root_.org.jetbrains.plugins.scala.statistics.CacheStatistics"
    val dom = tq"com.intellij.psi.PsiElement"

    //generated names
    val cachedFunName = TermName(c.freshName("cachedFun"))
    val dataName = TermName(c.freshName("data"))
    val dataTypeName = TypeName(c.freshName("Data"))

    def parameters: (Tree, Tree, Tree) = c.prefix.tree match {
      case q"new CachedMappedWithRecursionGuard(..$params)" if params.length == 3 => (params(0), params(1), params(2))
      case _ => abort("Wrong annotation parameters!")
    }

    def abort(s: String) = c.abort(c.enclosingPosition, s)

    //annotation parameters
    val (element, defaultValue, dependencyItem) = parameters

    annottees.toList match {
      case DefDef(mods, name, tpParams, paramss, retTp, rhs) :: Nil =>
        if (retTp.isEmpty) {
          abort("You must specify return type")
        }

        //some more generated names
        val keyId = c.freshName(name.toString + "cacheKey")
        val key = TermName(c.freshName(name.toString + "Key"))
        val cacheStatsName = TermName(c.freshName(name + "cacheStats"))

        val analyzeCaches = c.settings.contains(ANALYZE_CACHES)
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

        val rightHandSide =
          if (analyzeCaches) {
            val innerCachedFunName = TermName(c.freshName())
            //have to put it in a separate function because otherwise it falls with NonLocalReturn
            q"""
              def $innerCachedFunName(): $retTp = $rhs

              $cacheStatsName.recalculatingCache()
              val startTime = System.nanoTime()
              val res = $innerCachedFunName()
              val stopTime = System.nanoTime()
              $cacheStatsName.reportTimeToCalculate(stopTime - startTime)
              $cacheStatsName.addCacheObject(res)
              res
            """
          } else rhs

        val builder = q"""
          def $cachedFunName(${TermName(c.freshName())}: _root_.scala.Any, $dataName: $dataTypeName): $retTp = {
            ..$parameterDefinitions

            $rightHandSide
          }
          """

        val updatedRhs = q"""
          ${if (analyzeCaches) q"$cacheStatsName.aboutToEnterCachedArea()" else EmptyTree}
          type $dataTypeName = (..$parameterTypes)
          val $dataName = (..$parameterNames)

          $builder

          $methodFQN[$dom, $dataTypeName, $retTp]($element, $dataName, $key, $cachedFunName, $defaultValue, $dependencyItem)
        """

        val updatedDef = DefDef(mods, name, tpParams, paramss, retTp, updatedRhs)
        val res = q"""
          private val $key = $cachesUtilFQN.getOrCreateKey[$mappedKeyTypeFQN[(..$parameterTypes), $retTp]]($keyId)
          ${if (analyzeCaches) q"private val $cacheStatsName = $cacheStatisticsFQN($keyId, ${name.toString})" else EmptyTree}

          ..$updatedDef
        """
        println(res)
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }

  def cachedWithRecursionGuardImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._
    def abort(s: String) = c.abort(c.enclosingPosition, s)

    def parameters: (Tree, Tree, Tree, Tree, Boolean) = c.prefix.tree match {
      case q"new CachedWithRecursionGuard[$t](..$params)" if params.length == 3 =>
        (params.head, params(1), params(2), t, false) //false is for default parameter
      case q"new CachedWithRecursionGuard[$t](..$params)" if params.length == 4 =>
        val optional = params.last match {
          case q"useOptionalProvider = $v" => c.eval[Boolean](c.Expr(v))
          case q"$v" => c.eval[Boolean](c.Expr(v))
        }
        (params.head, params(1), params(2), t, optional)
      case _ => abort("Wrong annotation parameters!")
    }

    //fully qualified names
    val cachesUtilFQN = q"_root_.org.jetbrains.plugins.scala.caches.CachesUtil"
    val cachedValueFQN = tq"_root_.com.intellij.psi.util.CachedValue"
    val keyFQN = tq"_root_.com.intellij.openapi.util.Key"
    val cacheStatisticsFQN = q"_root_.org.jetbrains.plugins.scala.statistics.CacheStatistics"

    val (element, defaultValue, dependencyItem, providerType, useOptionalProvider) = parameters

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
        val analyzeCaches = c.settings.contains(ANALYZE_CACHES)

        val provider =
          if (useOptionalProvider) TypeName("MyOptionalProvider")
          else TypeName("MyProvider")



        val cachedFunRHS =
          if (analyzeCaches) {
            val innerCachedFunName = TermName(c.freshName())
            //have to put it in a separate function because otherwise it falls with NonLocalReturn
            q"""
              def $innerCachedFunName(): $retTp = $rhs

              $cacheStatsName.recalculatingCache()
              val startTime = System.nanoTime()
              val res = $innerCachedFunName()
              val stopTime = System.nanoTime()
              $cacheStatsName.reportTimeToCalculate(stopTime - startTime)
              $cacheStatsName.addCacheObject(res)
              res
            """
          } else rhs
        val fun = q"def $cachedFunName(): $retTp = $cachedFunRHS"
        val builder = q"new $cachesUtilFQN.$provider[$providerType, $retTp]($element, _ => $cachedFunName())($dependencyItem)"

        val updatedRhs = q"""
          ${if (analyzeCaches) q"$cacheStatsName.aboutToEnterCachedArea()" else EmptyTree}
          $fun
          $cachesUtilFQN.getWithRecursionPreventingWithRollback[$providerType, $retTp]($element, $keyVarName, $builder, $defaultValue)
          """
        val updatedDef = DefDef(mods, name, tpParams, params, retTp, updatedRhs)
        val res = q"""
          private val $keyVarName = $cachesUtilFQN.getOrCreateKey[$keyFQN[$cachedValueFQN[$retTp]]]($keyId)
          ${if (analyzeCaches) q"private val $cacheStatsName = $cacheStatisticsFQN($keyId, ${name.toString})" else EmptyTree}

          ..$updatedDef
          """
        println(res)
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }

  def cachedInsidePsiElementImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import c.universe._
    def abort(s: String) = c.abort(c.enclosingPosition, s)

    def parameters: (Tree, Tree, Boolean) = c.prefix.tree match {
      case q"new CachedInsidePsiElement(..$params)" if params.length == 2 => (params.head, params(1), false)
      case q"new CachedInsidePsiElement(..$params)" if params.length == 3 =>
        val optional = params.last match {
          case q"useOptionalProvider = $v" => c.eval[Boolean](c.Expr(v))
          case q"$v" => c.eval[Boolean](c.Expr(v))
        }
        (params.head, params(1), optional)
      case _ => abort("Wrong annotation parameters!")
    }

    //fully qualified names
    val cachesUtilFQN = q"_root_.org.jetbrains.plugins.scala.caches.CachesUtil"
    val cachedValueFQN = tq"_root_.com.intellij.psi.util.CachedValue"
    val keyFQN = tq"_root_.com.intellij.openapi.util.Key"
    val cacheStatisticsFQN = q"_root_.org.jetbrains.plugins.scala.statistics.CacheStatistics"

    //annotation parameters
    val (elem, dependencyItem, useOptionalProvider) = parameters

    annottees.toList match {
      case DefDef(mods, name, tpParams, paramss, retTp, rhs) :: Nil =>
        if (retTp.isEmpty) {
          abort("You must specify return type")
        }

        //generated names
        val cachedFunName = TermName(c.freshName("cachedFun"))
        val keyId = c.freshName(name.toString + "cacheKey")
        val key = TermName(c.freshName(name.toString + "Key"))
        val cacheStatsName = TermName(c.freshName("cacheStats"))

        val analyzeCaches = c.settings.contains(ANALYZE_CACHES)
        println(s"analyzeCaches: $analyzeCaches")
        println(s"compiler flags: ${c.settings}")

        val provider =
          if (useOptionalProvider) TypeName("MyOptionalProvider")
          else TypeName("MyProvider")

        val cachedFunRHS =
          if (analyzeCaches) {
            val innerCachedFunName = TermName(c.freshName())
            //have to put it in a separate function because otherwise it falls with NonLocalReturn
            q"""
              def $innerCachedFunName(): $retTp = $rhs

              $cacheStatsName.recalculatingCache()
              val startTime = System.nanoTime()
              val res = $innerCachedFunName()
              val stopTime = System.nanoTime()
              $cacheStatsName.reportTimeToCalculate(stopTime - startTime)
              $cacheStatsName.addCacheObject(res)
              res
            """
          } else rhs
        val updatedRhs = q"""
          def $cachedFunName(): $retTp = $cachedFunRHS

          ${if (analyzeCaches) q"$cacheStatsName.aboutToEnterCachedArea()" else EmptyTree}
          $cachesUtilFQN.get($elem, $key, new $cachesUtilFQN.$provider[Any, $retTp]($elem, _ => $cachedFunName())($dependencyItem))
          """
        val updatedDef = DefDef(mods, name, tpParams, paramss, retTp, updatedRhs)
        val res = q"""
          private val $key = $cachesUtilFQN.getOrCreateKey[$keyFQN[$cachedValueFQN[$retTp]]]($keyId)
          ${if (analyzeCaches) q"private val $cacheStatsName = $cacheStatisticsFQN($keyId, ${name.toString})" else EmptyTree}

          ..$updatedDef
          """
        println(res)
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
  type ModCount = Value
  val getModificationCount = Value("getModificationCount")
  val getOutOfCodeBlockModificationCount = Value("getOutOfCodeBlockModificationCount")
  val getJavaStructureModificationCount = Value("getJavaStructureModificationCount")
}
