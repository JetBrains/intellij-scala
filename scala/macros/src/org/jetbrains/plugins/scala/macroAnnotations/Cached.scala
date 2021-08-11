package org.jetbrains.plugins.scala.macroAnnotations

import org.jetbrains.annotations.Nls

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
 * If the annotated function has a parameter called `cacheMode`,
 * this parameter will not be used as part of the HashMap key,
 * but will change the caching behaviour. See [[org.jetbrains.plugins.scala.caches.CacheMode]]
 *
 * Author: Svyatoslav Ilinskiy
 * Date: 9/18/15.
 */
class Cached(modificationTracker: Object, psiElement: Any, trackedExpressions: Any*) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro Cached.cachedImpl
}

object Cached {
  def cachedImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import CachedMacroUtil._
    import c.universe._
    implicit val x: c.type = c

    def abort(@Nls message: String) = c.abort(c.enclosingPosition, message)

    def parameters: (Tree, Tree, Seq[Tree]) = {
      c.prefix.tree match {
        case q"new Cached(..$params)" if params.length >= 2 =>
          val Seq(modificationTracker, psiElement, tracked @ _*) = params.map(_.asInstanceOf[c.universe.Tree])
          (modificationTracker, psiElement, tracked)
        case _ => abort(MacrosBundle.message("macros.cached.wrong.parameters"))
      }
    }

    //annotation parameters
    val (modTracker, psiElement, trackedExprs) = parameters

    annottees.toList match {
      case (dd@DefDef(mods, nameTerm, tpParams, paramss, retTp, rhs)) :: Nil =>
        if (retTp.isEmpty) {
          abort(MacrosBundle.message("macros.cached.specify.return.type"))
        }
        val name = qualifiedTernName(nameTerm.toString)
        //generated names
        val cachedFunName = generateTermName(name, "$cachedFun")
        val tracerName = generateTermName(name, "$tracer")
        val cacheName = withClassName(nameTerm)

        val keyId = stringLiteral(name + "$cacheKey")

        val mapAndCounterRef = generateTermName(name, "$mapAndCounter")
        val timestampedDataRef = generateTermName(name,  "$valueAndCounter")

        //DefDef parameters
        val (flatParams, hasCacheModeParam) = {
          val params = paramss.flatten
          val (keyParams, cacheModeParams) = params.partition(_.name.toString != "cacheMode")
          assert(math.abs(cacheModeParams.size) <= 1)
          (keyParams, cacheModeParams.nonEmpty)
        }
        val paramNames = flatParams.map(_.name)
        val hasParameters: Boolean = flatParams.nonEmpty

        val (fields, updatedRhs) = if (hasParameters) {
          //wrap type of value in Some to avoid unboxing in putIfAbsent for primitive types
          val mapType = tq"$concurrentMapTypeFqn[(..${flatParams.map(_.tpt)}), _root_.scala.Some[$retTp]]"

          def createNewMap = q"new _root_.java.util.concurrent.ConcurrentHashMap()"

          val fields =
            q"""
              new _root_.scala.volatile()
              private val $mapAndCounterRef: $atomicReferenceTypeFQN[$timestampedTypeFQN[$mapType]] = {
                import $cacheCapabilitiesFQN._
                $cacheTrackerFQN.track($keyId, $cacheName) {
                  new $atomicReferenceTypeFQN($timestampedFQN(null, -1L))
                }
              }
            """

          val processCacheModeIfPresent =
            if (hasCacheModeParam)
              q"""
                {
                  def getCached = {
                    val map = $mapAndCounterRef.get.data
                    if (map == null) null
                    else map.get(key)
                  }
                  cacheMode match {
                    case $cacheModeFqn.ComputeIfOutdated =>
                    case $cacheModeFqn.ComputeIfNotCached =>
                      getCached match {
                        case Some(v) => return v
                        case null =>
                      }
                    case $cacheModeFqn.CachedOrDefault(default) =>
                      return getCached match {
                        case Some(v) => v
                        case null => default
                      }
                  }
                }
               """
            else q"()"

          def updatedRhs = q"""
             def $cachedFunName(): $retTp = $rhs

             val currModCount = $modTracker.getModificationCount()

             val $tracerName = ${internalTracerInstance(c)(keyId, cacheName, trackedExprs)}
             $tracerName.invocation()

             val key = (..$paramNames)

             $processCacheModeIfPresent

             val map = {
               val timestampedMap = $mapAndCounterRef.get
               if (timestampedMap.modCount < currModCount) {
                 $mapAndCounterRef.compareAndSet(timestampedMap, $timestampedFQN($createNewMap, currModCount))
               }
               $mapAndCounterRef.get.data
             }

             map.get(key) match {
               case Some(v) => v
               case null =>
                 val stackStamp = $recursionManagerFQN.markStack()

                 $tracerName.calculationStart()

                 val computed = try {
                   //null values are not allowed in ConcurrentHashMap, but we want to cache nullable functions
                   _root_.scala.Some($cachedFunName())
                 } finally {
                   $tracerName.calculationEnd()
                 }

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
              private val $timestampedDataRef: $atomicReferenceTypeFQN[$timestampedTypeFQN[$retTp]] = {
                import $cacheCapabilitiesFQN._
                $cacheTrackerFQN.track($keyId, $cacheName) {
                  new $atomicReferenceTypeFQN($timestampedFQN(${defaultValue(c)(retTp)}, -1L))
                }
              }
            """

          val processCacheModeIfPresent =
            if (hasCacheModeParam)
              q"""
                {
                  cacheMode match {
                    case $cacheModeFqn.ComputeIfNotCached if timestamped.modCount >= 0 =>
                      return timestamped.data
                    case $cacheModeFqn.CachedOrDefault(default) =>
                      return if (timestamped.modCount < 0) default
                             else timestamped.data
                    case _ =>
                  }
                }
               """
            else q"()"

          val updatedRhs =
            q"""
               def $cachedFunName(): $retTp = $rhs

               val currModCount = $modTracker.getModificationCount()

               val $tracerName = ${internalTracerInstance(c)(keyId, cacheName, trackedExprs)}
               $tracerName.invocation()

               val timestamped = $timestampedDataRef.get
               $processCacheModeIfPresent
               if (timestamped.modCount == currModCount) timestamped.data
               else {
                 val stackStamp = $recursionManagerFQN.markStack()

                 $tracerName.calculationStart()

                 val computed = try {
                   $cachedFunName()
                 } finally {
                   $tracerName.calculationEnd()
                 }

                 if (stackStamp.mayCacheNow()) {
                   $timestampedDataRef.compareAndSet(timestamped, $timestampedFQN(computed, currModCount))
                   $timestampedDataRef.get.data
                 }
                 else computed
               }

             """
          (fields, updatedRhs)
        }

        val updatedDef = DefDef(mods, nameTerm, tpParams, paramss, retTp, updatedRhs)
        val res = q"""
          ..$fields
          $updatedDef
          """
        CachedMacroUtil.debug(res)
        c.Expr(res)
      case _ => abort(MacrosBundle.message("macros.cached.only.annotate.one.function"))
    }
  }
}
