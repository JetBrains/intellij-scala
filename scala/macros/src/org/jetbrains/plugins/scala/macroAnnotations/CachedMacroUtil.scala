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

  def debug(a: Any): Unit = {
    if (debug) {
      Console.println(a)
    }
  }

  def cachesUtilFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.caches.CachesUtil"
  }

  def blockModificationTrackerFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.caches.BlockModificationTracker"
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

  def internalTracer(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.caches.stats.Tracer"
  }

  def recursionGuardFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"org.jetbrains.plugins.scala.caches.RecursionManager.RecursionGuard"
  }

  def recursionManagerFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"org.jetbrains.plugins.scala.caches.RecursionManager"
  }


  def psiModificationTrackerFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.com.intellij.psi.util.PsiModificationTracker"
  }

  def psiElementType(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.com.intellij.psi.PsiElement"
  }

  def scalaPsiManagerFQN(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    q"_root_.org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager"
  }

  def concurrentMapTypeFqn(implicit c: whitebox.Context): c.universe.Tree = {
    import c.universe.Quasiquote
    tq"_root_.java.util.concurrent.ConcurrentMap"
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

  def abort(s: String)(implicit c: whitebox.Context): Nothing = c.abort(c.enclosingPosition, s)

  def box(c: whitebox.Context)(tp: c.universe.Tree): c.universe.Tree = {
    import c.universe.Quasiquote
    tp match {
      case tq"Boolean" => tq"java.lang.Boolean"
      case tq"Int" => tq"java.lang.Integer"
      case tq"Long" => tq"java.lang.Long"
      case _ => tp
    }
  }

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
            q"$blockModificationTrackerFQN($psiElement)"
          case Some(ModCount.getModificationCount) => q"$psiModificationTrackerFQN.SERVICE.getInstance($psiElement.getProject)"
          case Some(ModCount.`anyScalaPsiModificationCount`) =>
            q"$scalaPsiManagerFQN.AnyScalaPsiModificationTracker"
          case _ => tree
        }
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

  def doPreventingRecursion(c: whitebox.Context)(computation: c.universe.Tree,
                                                 guard: c.universe.TermName,
                                                 data: c.universe.TermName,
                                                 resultType: c.universe.Tree): c.universe.Tree = {
    import c.universe.Quasiquote

    val needLocalFunction = hasReturnStatements(c)(computation)
    if (needLocalFunction) {
      abort("Annotated function has explicit return statements, function body can't be inlined")(c)
    }

    q"""val realKey = $guard.createKey($data)

        val (sizeBefore, sizeAfter) = $guard.beforeComputation(realKey)
        try {
          $computation
        }
        finally {
          $guard.afterComputation(realKey, sizeBefore, sizeAfter)
        }
     """
  }
  def hasReturnStatements(c: whitebox.Context)(tree: c.universe.Tree): Boolean = {
    var result = false
    val traverser = new c.universe.Traverser {
      override def traverse(tree: c.universe.Tree): Unit = tree match {
        case c.universe.Return(_) => result = true
        //skip local functions and classes
        case c.universe.DefDef(_, _, _, _, _, _) =>
        case c.universe.ClassDef(_, _, _, _) =>
        case c.universe.ModuleDef(_, _, _) =>
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
  //any physical psi change
  val getModificationCount = Value("getModificationCount")

  //only changes that may affect return type of a current block
  val getBlockModificationCount = Value("getBlockModificationCount")

  //Use for hot methods: it has minimal overhead, but updates on each change
  //
  // PsiModificationTracker is not an option, because it
  // - requires calling getProject first
  // - doesn't work for non-physical elements
  val anyScalaPsiModificationCount = Value("anyScalaPsiModificationCount")
}
