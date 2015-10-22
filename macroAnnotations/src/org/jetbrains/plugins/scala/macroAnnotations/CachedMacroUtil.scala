package org.jetbrains.plugins.scala.macroAnnotations

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

  def getMappedWithRecursionFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"$cachesUtilFQN.getMappedWithRecursionPreventingWithRollback"
  }

  def mappedKeyTypeFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.org.jetbrains.plugins.scala.caches.CachesUtil.MappedKey"
  }

  def psiElementType(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.com.intellij.psi.PsiElement"
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
      val startTime = System.nanoTime()
      val res = $innerCachedFunName()
      val stopTime = System.nanoTime()
      $cacheStatsName.reportTimeToCalculate(stopTime - startTime)
      $cacheStatsName.addCacheObject(res)
      res
    """
    } else q"$rhs"
  }

  def analyzeCachesEnabled(c: whitebox.Context): Boolean = c.settings.contains(ANALYZE_CACHES)
}

object ModCount extends Enumeration {
  type ModCount = Value
  val getModificationCount = Value("getModificationCount")
  val getOutOfCodeBlockModificationCount = Value("getOutOfCodeBlockModificationCount")
  val getJavaStructureModificationCount = Value("getJavaStructureModificationCount")
}
