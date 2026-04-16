package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinitionLike}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.resolve.StdKinds._
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult, ScalaResolveState}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl.ScDocResolvableCodeReferenceImpl.SelectorKind

class ScDocResolvableCodeReferenceImpl(node: ASTNode) extends ScStableCodeReferenceImpl(node) with ScDocResolvableCodeReference {

  override protected def walkUpIntermediaryPackages: Boolean = true

  override protected def debugKind: Option[String] = Some("scalaDoc")

  protected def isTopLevelSearch: Boolean = true

  //noinspection RedundantDefaultArgument
  override def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult] = cachedWithRecursionGuard("multiResolveScala", this, ScalaResolveResult.EMPTY_ARRAY, BlockModificationTracker(this), Tuple1(incomplete)) {
    multiResolveScalaImpl()
  }

  private def multiResolveScalaImpl(): Array[ScalaResolveResult] = {
    val ref = this

    val refName = ref.refName

    val (selectorKind, refNameAdjusted) = SelectorKind.stripFrom(refName)

    def processResult(resolveResult0: Array[ScalaResolveResult]): Array[ScalaResolveResult] = {
      if (resolveResult0.isEmpty) return Array.empty
      //De-duplicating resolve results.
      //DETAILS: When we have a reference to a class method: [[org.MyClass.myMethod]] for a class with a companion object
      //qualifier `org.MyClass` is resolved to 2 targets: the class and it's companion object.
      //Then for every resolved qualifier `myMethod` is resolved to the same member of the class (yes, even for the object ¯\_(ツ)_/¯)
      //(search for `ScDocResolvableCodeReference` usages in `org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl.processQualifier
      val resolveResult1 = resolveResult0.distinctBy(_.element)

      def isTerm(rr: ScalaResolveResult): Boolean = rr.element match {
        case _: ScObject => true
        case _: ScTypeDefinitionLike => false
        case _: PsiClass => false
        case _ => true
      }

      val resolveResult2 = selectorKind match {
        case SelectorKind.ForceTerm => resolveResult1.filter(isTerm)
        case SelectorKind.ForceType => resolveResult1.filterNot(isTerm)
        case SelectorKind.NoForce => resolveResult1
      }

      //If we resolved to multiple references: class and it's companion, move companion object to the end
      //NOTE: it's not correct behaviour, but it will be incorrect until SCL-13263 is implemented
      //With this quick fix at least tests will be deterministic
      val resolveResult3 = resolveResult2.sortBy(r => if (r.element.is[ScObject]) 1 else 0)

      val resolveResult4 = resolveResult3.map {
        case ScalaResolveResult(constructor: ScPrimaryConstructor, _) if constructor.containingClass != null =>
          new ScalaResolveResult(constructor.containingClass)
        case result =>
          result
      }
      resolveResult4
    }

    val kinds = stableImportSelector
    val processor = new ResolveProcessor(kinds, ref, refNameAdjusted)

    if (isTopLevelSearch) {
      def nonEmptyOr(a: => Array[ScalaResolveResult], b: => Array[ScalaResolveResult]): Array[ScalaResolveResult] = {
        val result = a
        if (result.nonEmpty) result else b
      }

      def searchThrough(e: Option[PsiElement]): Array[ScalaResolveResult] = {
        e match {
          case Some(pkg) =>
            pkg.processDeclarations(processor, ScalaResolveState.empty, null, ref)
            processResult(processor.candidates)
          case None =>
            Array.empty
        }
      }

      val scope = getResolveScope
      val manager = ScalaPsiManager.instance
      val topLevelResults = nonEmptyOr(
        searchThrough(manager.getCachedClass(scope, "scala.Predef")),
        nonEmptyOr(
          searchThrough(manager.getCachedPackage("scala").map(ScPackageImpl.apply)),
          searchThrough(manager.emptyNamePackage)
        )
      )

      if (topLevelResults.nonEmpty) {
        return topLevelResults
      }
    }

    processResult(ref.doResolve(processor))
  }

  override def getKinds(incomplete: Boolean, completion: Boolean): ResolveTargets.ValueSet =
    stableImportSelector
}

object ScDocResolvableCodeReferenceImpl {
  type SelectorKind = SelectorKind.Value
  object SelectorKind extends Enumeration {
    val ForceTerm, ForceType, NoForce = Value

    def stripFrom(text: String): (SelectorKind, String) = {
      val len = text.length
      if (len < 1) {
        return (NoForce, text)
      }

      text.last match {
        case '!' => (ForceType, text.stripSuffix("!"))
        case '$' => (ForceTerm, text.stripSuffix("$"))
        case '`' if len >= 2 =>
          text(len - 2) match {
            case '!' => (ForceType, text.substring(0, len - 2) + "`")
            case '$' => (ForceTerm, text.substring(0, len - 2) + "`")
            case _ => (NoForce, text)
          }
        case _ => (NoForce, text)
      }
    }
  }
}