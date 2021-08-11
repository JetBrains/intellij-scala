package org.jetbrains.plugins.scala.macroAnnotations

import org.jetbrains.annotations.Nls

import scala.annotation.{nowarn, tailrec}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 9/22/15.
  */
object CachedMacroUtil {
  val debug: Boolean = false

  def debug(a: Any): Unit = {
    if (debug) {
      Console.println(a)
    }
  }

  def cachesUtilFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.caches.CachesUtil"
  }

  def modTrackerFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.caches.ModTracker"
  }

  def timestampedFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.caches.CachesUtil.Timestamped"
  }

  def timestampedTypeFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.org.jetbrains.plugins.scala.caches.CachesUtil.Timestamped"
  }

  def atomicReferenceTypeFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.java.util.concurrent.atomic.AtomicReference"
  }

  def cacheTrackerFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.caches.stats.CacheTracker"
  }

  def cacheCapabilitiesFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.caches.CachesUtil.CacheCapabilties"
  }

  def cleanupSchedulerTypeFqn(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.org.jetbrains.plugins.scala.caches.CleanupScheduler"
  }

  @nowarn("msg=checked")
  def defaultValue(c: whitebox.Context)(tp: c.universe.Tree): c.universe.Tree = {
    import c.universe.Quasiquote
    tp match {
      case tq"Boolean" => q"false"
      case tq"Int" => q"0"
      case tq"Long" => q"0L"
      case _ => q"null"
    }
  }

  def cachedValueTypeFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.com.intellij.psi.util.CachedValue"
  }

  def cachedValueProviderResultTypeFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.com.intellij.psi.util.CachedValueProvider.Result"
  }

  def keyTypeFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.com.intellij.openapi.util.Key"
  }

  private def internalTracer(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.caches.stats.Tracer"
  }

  def internalTracerInstance(c: whitebox.Context)(cacheKey: c.universe.Tree, cacheName: c.universe.Tree, trackedExprs: Seq[c.universe.Tree]): c.universe.Tree = {
    implicit val ctx: c.type = c
    import c.universe.Quasiquote

    if (trackedExprs.nonEmpty) {

      if (!expressionTracersEnabled(c)) {
        val message =
          MacrosBundle.message("macros.cached.expression.tracers.are.enabled.only.for.debug.and.tests").stripMargin
        abort(message)
      }

      val tracingSuffix = expressionsWithValuesText(c)(trackedExprs)
      val tracingKeyId = q"$cacheKey + $tracingSuffix"
      val tracingKeyName = q"$cacheName + $tracingSuffix"
      q"$internalTracer($tracingKeyId, $tracingKeyName)"
    }
    else q"$internalTracer($cacheKey, $cacheName)"


  }

  private def expressionsWithValuesText(c: whitebox.Context)(trees: Seq[c.universe.Tree]): c.universe.Tree = {
    import c.universe.Quasiquote

    val textTrees = trees.map(p => q"""" " + ${p.toString} + " == " + $p.toString""")
    val concatenation = textTrees match {
      case Seq(t) => q"$t"
      case ts     =>
        def concat(t1: c.universe.Tree, t2: c.universe.Tree) = q"""$t1 + "," + $t2"""
        q"${ts.reduce((t1, t2) => concat(t1, t2))}"
    }

    concatenation
  }

  //expression tracing may have unlimited performance overhead
  //to prevent it's accidental usage in production, compilation will fail on teamcity
  private def expressionTracersEnabled(c: whitebox.Context): Boolean = {
    System.getProperty("ij.scala.compile.server") == "true" || c.settings.contains("enable-expression-tracers")
  }

  def recursionGuardFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"org.jetbrains.plugins.scala.caches.RecursionManager.RecursionGuard"
  }

  def recursionManagerFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"org.jetbrains.plugins.scala.caches.RecursionManager"
  }

  def psiElementType(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.com.intellij.psi.PsiElement"
  }

  def concurrentMapTypeFqn(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.java.util.concurrent.ConcurrentMap"
  }

  def cacheModeFqn(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.caches.CacheMode"
  }

  def thisFunctionFQN(name: String)(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"""getClass.getName ++ "." ++ $name"""
  }

  def generateTermName(prefix: String = "", postfix: String = "")(implicit c: whitebox.Context): c.universe.TermName = {
    c.universe.TermName(prefix + "$" + postfix)
  }

  def stringLiteral(name: AnyRef)(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"${name.toString}"
  }

  def withClassName(name: AnyRef)(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    @nowarn("cat=deprecation")
    val enclosingName = c.enclosingClass match {
      case df: c.universe.DefTreeApi @unchecked => df.name.toString
      case _ => "________"
    }
    q"${enclosingName + "." + name.toString}"
  }

  @nowarn("cat=deprecation")
  def qualifiedTernName(termName: String)
                       (implicit c: whitebox.Context): String = {
    c.enclosingClass.symbol.fullName.replace('.', '$') + '$' + termName
  }

  def abort(@Nls s: String)(implicit c: whitebox.Context): Nothing = c.abort(c.enclosingPosition, s)

  def extractCacheModeParameter(c: whitebox.Context)(paramClauses: List[List[c.universe.ValDef]]): (List[c.universe.ValDef], Boolean) = {
    val params = paramClauses.flatten
    val (cacheModeParams, keyParams) = params.partition(_.name.toString == "cacheMode")
    assert(math.abs(cacheModeParams.size) <= 1)
    (keyParams, cacheModeParams.nonEmpty)
  }

  def preventCacheModeParameter(c: whitebox.Context)(paramClauses: List[List[c.universe.ValDef]]): Unit = {
    val params = paramClauses.flatten
    val hasCacheMode = params.exists(_.name.toString == "cacheMode")
    if (hasCacheMode) {
      abort(MacrosBundle.message("macros.cached.macro.does.not.support.cachemode.parameter"))(c)
    }
  }

  @nowarn("cat=unchecked")
  def box(c: whitebox.Context)(tp: c.universe.Tree): c.universe.Tree = {
    import c.universe.Quasiquote
    tp match {
      case tq"Boolean" => tq"java.lang.Boolean"
      case tq"Int" => tq"java.lang.Integer"
      case tq"Long" => tq"java.lang.Long"
      case _ => tp
    }
  }

  def withUIFreezingGuard(c: whitebox.Context)(tree: c.universe.Tree, retTp: c.universe.Tree): c.universe.Tree = {
    import c.universe.Quasiquote
    val fqName = q"_root_.org.jetbrains.plugins.scala.util.UIFreezingGuard"
    q"""val __guardedResult__ : $retTp =
          if ($fqName.isAlreadyGuarded) { $tree }
          else $fqName.withResponsibleUI { $tree }

        __guardedResult__
     """
  }

  abstract class UpdateHolderGenerator[C <: whitebox.Context](val c: C) {
    //generated expression should evaluate to the existing value if it is present
    def apply(resultName: c.universe.TermName): c.universe.Tree
  }

  def doPreventingRecursionCaching(c: whitebox.Context)(computation: c.universe.Tree,
                                                        guard: c.universe.TermName,
                                                        data: c.universe.TermName,
                                                        updateHolderGenerator: UpdateHolderGenerator[c.type]): c.universe.Tree = {
    import c.universe.Quasiquote
    implicit val context: c.type = c

    val needLocalFunction = hasReturnStatements(c)(computation)
    if (needLocalFunction) {
      abort(MacrosBundle.message("annotated.function.cannot.be.inlined.because.of.return"))(c)
    }

    val resultName = c.universe.TermName("result")
    q"""{
          val fromLocalCache = $guard.getFromLocalCache($data)
          if (fromLocalCache != null) fromLocalCache
          else {
            val realKey = $guard.createKey($data)

            val (sizeBefore, sizeAfter, minDepth, localCacheBefore) = $guard.beforeComputation(realKey)
            val ($resultName, shouldCache) = try {
              val stackStamp = $recursionManagerFQN.markStack()
              val result = $computation
              val shouldCache = stackStamp.mayCacheNow()
              (result, shouldCache)
            }
            finally {
              $guard.afterComputation(realKey, sizeBefore, sizeAfter, minDepth, localCacheBefore)
            }

            if (shouldCache) {
              val race = ${updateHolderGenerator(resultName)}

              if (race != null) race
              else $resultName
            } else {
              // we should not cache, because the value is tainted by an intercepted recursion
              // but we can cache it in the local cache to prevent calculating it again in
              // same situations
              // See CacheWithinRecursionTest.test_local_cache/test_local_cache_reset
              $guard.cacheInLocalCache($data, $resultName)
              $resultName
            }
          }
        }
     """
  }


  def hasReturnStatements(c: whitebox.Context)(tree: c.universe.Tree): Boolean = {
    var result = false
    val traverser = new c.universe.Traverser {
      override def traverse(tree: c.universe.Tree): Unit = tree match {
        case _: c.universe.Return @unchecked => result = true
        //skip local functions and classes
        case _: c.universe.DefDef @unchecked =>
        case _: c.universe.ClassDef @unchecked =>
        case _: c.universe.ModuleDef @unchecked =>
        case _ => super.traverse(tree)
      }
    }
    traverser.traverse(tree)
    result
  }

  def handleProbablyRecursiveException(c: whitebox.Context)
                                      (elemName: c.universe.TermName,
                                       dataName: c.universe.TermName,
                                       keyName: c.universe.TermName,
                                       calculation: c.universe.Tree): c.universe.Tree = {
    import c.universe.Quasiquote

    q"""
        try {
          $calculation
        }
        catch {
          case exc: org.jetbrains.plugins.scala.caches.CachesUtil.ProbablyRecursionException[_] if exc.key == $keyName =>
            if (exc.elem == $elemName && exc.data == $dataName) {
              try {
                $calculation
              } finally {
                exc.set.foreach { fun =>
                  fun.isProbablyRecursive = false
                }
              }
            }
            else {
              val fun = com.intellij.psi.util.PsiTreeUtil.getContextOfType($elemName, true,
                classOf[org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction])
              if (fun == null || fun.isProbablyRecursive) throw exc
              else {
                fun.isProbablyRecursive = true
                throw exc.copy(set = exc.set + fun)
              }
            }
        }
     """
  }

  def getOrCreateKey(c: whitebox.Context, hasParams: Boolean)
                    (keyId: c.universe.Tree, dataType: c.universe.Tree, resultType: c.universe.Tree)
                    (implicit c1: c.type): c.universe.Tree = {

    import c.universe.Quasiquote

    if (hasParams) q"$cachesUtilFQN.getOrCreateKey[$cachesUtilFQN.CachedMap[$dataType, $resultType]]($keyId)"
    else q"$cachesUtilFQN.getOrCreateKey[$cachesUtilFQN.CachedRef[$resultType]]($keyId)"
  }

}

object ModCount extends Enumeration {
  type ModCount = Value
  //only changes that may affect return type of a current block
}
