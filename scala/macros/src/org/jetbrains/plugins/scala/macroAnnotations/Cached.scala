package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
  * If you annotate a function with @Cached annotation, the compiler will generate code to cache it.
  *
  * If an annotated function has parameters, one field will be generated (a HashMap).
  * If an annotated function has no parameters, two fields will be generated: result and modCount
  *
  * NOTE !IMPORTANT!: function annotated with @Cached must be on top-most level because generated code generates fields
  * right outside the cached function and if this function is inner it won't work.
  *
  * Author: Svyatoslav Ilinskiy
  * Date: 9/18/15.
  */
class Cached(dependencyItem: Object, psiElement: Any) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro Cached.cachedImpl
}

object Cached {
  def cachedImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import CachedMacroUtil._
    import c.universe._
    implicit val x: c.type = c

    def abort(message: String) = c.abort(c.enclosingPosition, message)

    def parameters: (Tree, Tree) = {
      c.prefix.tree match {
        case q"new Cached(..$params)" if params.length == 2 =>
          val depItem = params(0).asInstanceOf[c.universe.Tree]
          val psiElement = params(1).asInstanceOf[c.universe.Tree]
          val modTracker = modCountParamToModTracker(c)(depItem, psiElement)
          (modTracker, psiElement)
        case _ => abort("Wrong parameters")
      }
    }

    //annotation parameters
    val (modTracker, psiElement) = parameters

    annottees.toList match {
      case DefDef(mods, name, tpParams, paramss, retTp, rhs) :: Nil =>
        if (retTp.isEmpty) {
          abort("You must specify return type")
        }
        //generated names
        val cachedFunName = generateTermName("cachedFun")
        val cacheStatsName = generateTermName("cacheStats")
        val keyId = c.freshName(name.toString + "$cacheKey")
        val mapAndCounterRef = generateTermName(name.toString + "$mapAndCounter")
        val timestampedDataRef = generateTermName(name.toString + "$valueAndCounter")

        val analyzeCaches = analyzeCachesEnabled(c)
        val defdefFQN = thisFunctionFQN(name.toString)

        //DefDef parameters
        val flatParams = paramss.flatten
        val paramNames = flatParams.map(_.name)
        val hasParameters: Boolean = flatParams.nonEmpty

        val analyzeCachesField =
          if(analyzeCaches) q"private val $cacheStatsName = $cacheStatisticsFQN($keyId, $defdefFQN)"
          else EmptyTree

        val actualCalculation = transformRhsToAnalyzeCaches(c)(cacheStatsName, retTp, rhs)

        val currModCount = q"val currModCount = $modTracker.getModificationCount()"

        val (fields, updatedRhs) = if (hasParameters) {
          //wrap type of value in Some to avoid unboxing in putIfAbsent for primitive types
          val mapType = tq"$concurrentMapTypeFqn[(..${flatParams.map(_.tpt)}), _root_.scala.Some[$retTp]]"

          def createNewMap = q"_root_.com.intellij.util.containers.ContainerUtil.newConcurrentMap()"

          val fields = q"""
              new _root_.scala.volatile()
              private val $mapAndCounterRef: $atomicReferenceTypeFQN[$timestampedTypeFQN[$mapType]] =
                new $atomicReferenceTypeFQN($timestampedFQN(null, -1L))

              ..$analyzeCachesField
           """

          val getOrUpdateMapDef = q"""
              def getOrUpdateMap() = {
                val timestampedMap = $mapAndCounterRef.get
                if (timestampedMap.modCount < currModCount) {
                  $mapAndCounterRef.compareAndSet(timestampedMap, $timestampedFQN($createNewMap, currModCount))
                }
                $mapAndCounterRef.get.data
              }
            """

          def updatedRhs = q"""
             def $cachedFunName(): $retTp = {
               $actualCalculation
             }

             ..$currModCount

             $getOrUpdateMapDef

             val map = getOrUpdateMap()
             val key = (..$paramNames)

             map.get(key) match {
               case Some(v) => v
               case null =>
                 val stackStamp = $recursionManagerFQN.markStack()

                 //null values are not allowed in ConcurrentHashMap, but we want to cache nullable functions
                 val computed = _root_.scala.Some($cachedFunName())

                 if (stackStamp.mayCacheNow()) {
                   val race = map.putIfAbsent(key, computed)
                   if (race != null) race.get
                   else computed.get
                 }
                 else computed.get

             }
          """
          (fields, updatedRhs)
        } else {
          val fields = q"""
              new _root_.scala.volatile()
              private val $timestampedDataRef: $atomicReferenceTypeFQN[$timestampedTypeFQN[$retTp]] =
                new $atomicReferenceTypeFQN($timestampedFQN(${defaultValue(c)(retTp)}, -1L))

            ..$analyzeCachesField
          """

          val getOrUpdateValue =
            q"""
               val timestamped = $timestampedDataRef.get
               if (timestamped.modCount == currModCount) timestamped.data
               else {
                 val stackStamp = $recursionManagerFQN.markStack()

                 val computed = $cachedFunName()

                 if (stackStamp.mayCacheNow()) {
                   $timestampedDataRef.compareAndSet(timestamped, $timestampedFQN(computed, currModCount))
                   $timestampedDataRef.get.data
                 }
                 else computed
               }
             """

          val updatedRhs =
            q"""
               def $cachedFunName(): $retTp = {
                 $actualCalculation
               }

               ..$currModCount

               $getOrUpdateValue
             """
          (fields, updatedRhs)
        }

        val updatedDef = DefDef(mods, name, tpParams, paramss, retTp, updatedRhs)
        val res = q"""
          ..$fields
          $updatedDef
          """
        CachedMacroUtil.println(res)
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }
}
