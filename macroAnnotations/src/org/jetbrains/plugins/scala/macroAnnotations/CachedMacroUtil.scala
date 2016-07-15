package org.jetbrains.plugins.scala.macroAnnotations

import scala.annotation.tailrec
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/22/15.
 */
object CachedMacroUtil {
  val debug: Boolean = false
  //to analyze caches pass in the following compiler flag: "-Xmacro-settings:analyze-caches"
  val ANALYZE_CACHES: String = "analyze-caches"

  def println(a: Any): Unit = {
    if (debug) {
      Console.println(a)
    }
  }

  def cachesUtilFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.caches.CachesUtil"
  }

  def cachedValueTypeFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.com.intellij.psi.util.CachedValue"
  }

  def keyTypeFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.com.intellij.openapi.util.Key"
  }

  def cacheStatisticsFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.statistics.CacheStatistics"
  }

  def getRecursionGuardFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"$cachesUtilFQN.getRecursionGuard"
  }

  def psiModificationTrackerFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.com.intellij.psi.util.PsiModificationTracker"
  }

  def mappedKeyTypeFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.org.jetbrains.plugins.scala.caches.CachesUtil.MappedKey"
  }

  def psiElementType(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.com.intellij.psi.PsiElement"
  }

  def scalaPsiManagerFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager"
  }

  def thisFunctionFQN(name: String)(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"""getClass.getName ++ "." ++ $name"""
  }

  def generateTermName(name: String = "")(implicit c: whitebox.Context): c.universe.TermName = {
    c.universe.TermName(c.freshName(name))
  }

  def generateTypeName(name: String = "")(implicit c: whitebox.Context): c.universe.TypeName = {
    c.universe.TypeName(c.freshName(name))
  }

  def abort(s: String)(implicit c: whitebox.Context): Nothing = c.abort(c.enclosingPosition, s)

  def transformRhsToAnalyzeCaches(c: whitebox.Context)(cacheStatsName: c.universe.TermName, retTp: c.universe.Tree, rhs: c.universe.Tree): c.universe.Tree = {
    import c.universe.Quasiquote
    if (analyzeCachesEnabled(c)) {
      val innerCachedFunName = generateTermName("")(c)
      //have to put it in a separate function because otherwise it falls with NonLocalReturn
      q"""
        def $innerCachedFunName(): $retTp = $rhs

        $cacheStatsName.recalculatingCache()
        val myStartTimes = ${cachesUtilFQN(c)}.timeToCalculateForAnalyzingCaches.get()
        val prevStartTime = Option(myStartTimes.tryPop()) //try to get time when previous cache started
        val timePrevCacheRanUntilThisCacheStarted = prevStartTime.map { case time: Long => System.nanoTime - time }
        myStartTimes.push(System.nanoTime()) //push my start time onto the stack
        val res = $innerCachedFunName()
        val stopTime = System.nanoTime()
        $cacheStatsName.reportTimeToCalculate(stopTime - myStartTimes.pop()) //how much time did this cache run
        $cacheStatsName.addCacheObject(res)
        //update the start time of the previous cache. It is basically increaced by the time this cache ran
        timePrevCacheRanUntilThisCacheStarted.foreach { case time: Long => myStartTimes.push(System.nanoTime - time)}
        res
      """
    } else q"$rhs"
  }

  def analyzeCachesEnabled(c: whitebox.Context): Boolean = c.settings.contains(ANALYZE_CACHES)

  @tailrec
  def modCountParamToModTracker(c: whitebox.Context)(tree: c.universe.Tree, psiElement: c.universe.Tree): c.universe.Tree = {
    implicit val x: c.type = c
    import c.universe._
    tree match {
      case q"modificationCount = $v" =>
        modCountParamToModTracker(x)(v, psiElement)
      case q"ModCount.$v" =>
        modCountParamToModTracker(x)(q"$v", psiElement)
      case q"$v" =>
        ModCount.values.find(_.toString == v.toString) match {
          case Some(ModCount.getBlockModificationCount) =>
            q"$cachesUtilFQN.enclosingModificationOwner($psiElement)"
          case Some(ModCount.getOutOfCodeBlockModificationCount) =>
            q"$scalaPsiManagerFQN.instance($psiElement.getProject).getModificationTracker"
          case Some(ModCount.getModificationCount) => q"$psiModificationTrackerFQN.MODIFICATION_COUNT"
          case Some(ModCount.getJavaStructureModificationCount) =>
            q"$psiModificationTrackerFQN.JAVA_STRUCTURE_MODIFICATION_COUNT"
          case _ => tree
        }
    }
  }

}

object ModCount extends Enumeration {
  type ModCount = Value
  val getModificationCount = Value("getModificationCount")
  val getOutOfCodeBlockModificationCount = Value("getOutOfCodeBlockModificationCount")
  val getJavaStructureModificationCount = Value("getJavaStructureModificationCount")
  val getBlockModificationCount = Value("getBlockModificationCount")
}
