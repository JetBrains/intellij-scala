package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.caches.ModTracker.anyScalaPsiChange
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline.{Conditions, ShouldProcess}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isImplicit, isOnlyVisibleInLocalFile, superTypeSignatures, superValsSignatures}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScRefinement, ScSelfTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.ScalaMainMethodUtil

import java.util
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable.ArrayBuffer

/**
 * Welcome to the cheapRefSearch package!
 *
 * This package models and implements various concepts that are essential in multi-purpose, cheap reference searching.
 *
 * We need cheap reference searching mainly for inspections that cannot afford to perform true reference searching,
 * which is an inherently expensive routine.
 *
 * All things cheap come with a fine print. cheapRefSearch aims to be correct most of the times. If you need "always
 * correct", cheapRefSearch is not for you.
 */

private[declarationRedundancy] object Search {

  type Usages = Seq[ElementUsage]

  /**
   * `CanExit` is the lowest-level control flow mechanism in cheapRefSearch. It is the single most important concept in
   * this package. Please try to understand its purpose fully before making modifications to cheapRefSearch.
   *
   * You provide a CanExit instance to a [[Search.Pipeline]] once, and it is called by a [[Search.Method]] every time it
   * has found a usage of a given element. For technical reasons it's also called before the pipeline proceeds to the
   * next search method, and when a cached result from a previous search was found. But its main purpose is to give
   * search methods the capability to stop searching for more usages before they have exhausted their total search
   * space.
   *
   * If your CanExit returns `true` for a given usage, the search method will not collect any further usages.
   * Additionally the search pipeline will not proceed to its next stage (i.e. the next-in-line search method).
   * Pipeline execution is now complete, and the pipeline returns the usages that were collected.
   *
   * Conversely, if your CanExit returns `false` for a given usage, the search method will continue looking for more
   * usages.
   *
   * CanExit puts control of when to stop searching in the hands of the pipeline consumer (i.e. inspection). It allows
   * one consumer to say "Stop searching after finding any kind of usage", as is the case for unused declaration
   * inspection, while another consumer can say "Stop searching after finding a usage that would be invalid if the
   * element under scrutiny would be `private`", as is the case for can-be-private inspection.
   *
   * <b>Example 1</b> -- Stop searching if there is at least one usage
   * <pre><code>
   * val canExit: CanExit = _ => true
   * </code></pre>
   *
   * <b>Example 2</b> -- Stop searching if the total search space has been exhausted
   * <pre><code>
   * val canExit: CanExit = _ => false
   * </code></pre>
   *
   * <b>Example 3</b> -- Stop searching when encountering a reference of type `T`
   * <pre><code>
   * val canExit: CanExit = (usage: ElementUsage) => usage match {
   * case u: ElementUsageWithReference => u.reference.isInstanceOf[T]
   * case _ => false
   * }
   * </code></pre>
   */
  type CanExit = ElementUsage => Boolean

  /**
   * `Pipeline` serves as the entry point for cheapRefSearch's services. For example usages see
   * [[org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspection]] and
   * [[org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection]].
   *
   * A `Pipeline` instance is reusable without requiring the consumer to perform any kind of state management
   * between searches. As long as your search requires the same `Search.Method`s and `CanExit` logic, you can
   * reuse the same instance.
   *
   * @param searchMethods The collection of `Search.Method`s that this pipeline will execute sequentially, and
   *                      in the provided order.
   * @param canExit       The condition that should lead the pipeline to gracefully conclude execution.
   */
  private[declarationRedundancy] final class Pipeline(val searchMethods: Seq[Method], canExit: CanExit) {

    /**
     * Initiates a usages search and returns the results.
     *
     * @param element    The element for which the pipeline should find usages.
     * @param isOnTheFly When true, a somewhat cheaper logic will be used if possible. For more details see
     *                   [[SearchMethodsWithProjectBoundCache]].
     *                   Typically you provide `false` for batch inspections, and `true` for on-the-fly ones.
     * @return A collection of `ElementUsage`s. Remember that this collection may or may not contain all usages that
     *         cheapRefSearch is able to find, depending on the `CanExit` and `Search.Method`s of the pipeline.
     */
    def runSearchPipeline(element: ScNamedElement, isOnTheFly: Boolean): Usages = {

      val result = ArrayBuffer.empty[ElementUsage]

      val ctx = new Context(element, canExit)

      val isMemberOfUnusedTypeDefinition = element match {
        case m: ScMember =>
          val containingClass = m.containingClass
          containingClass != null &&
            Util.shouldProcessElement(containingClass) &&
            runSearchPipeline(containingClass, isOnTheFly).isEmpty
        case _ => false
      }

      val isScalaClass = element.is[ScClass]

      val conditions = new Conditions(
        isOnTheFly,
        isOnlyVisibleInLocalFile(element),
        isImplicit(element),
        isMemberOfUnusedTypeDefinition,
        isScalaClass
      )

      def isSelfReferentialTypeDefRef(usage: ElementUsage): Boolean = (usage, element.asOptionOf[ScTypeDefinition]) match {
        case (usageWithReference: ElementUsageWithKnownReference, Some(typeDef)) =>
          usageWithReference.referenceIsInMemberThatHasTypeDefAsAncestor(typeDef)
        case _ => false
      }

      //println("SearchPipeline.runSearchPipeline")

      searchMethods
        .iterator
        .filter(_.shouldProcess(conditions))
        .takeWhile(_ => !result.exists(ctx.canExit))
        .foreach { method =>
          //println(s"Run search method ${method.getClass.getSimpleName}")
          method.getUsages(ctx)
            .filterNot(isSelfReferentialTypeDefRef)
            .foreach { usage =>
              //println(s"Found: $usage")
              result.addOne(usage)
            }
        }

      //println(s"SearchPipeline.runSearchPipeline result: $result")
      result.toSeq
    }
  }

  /**
   * The result of a single search method's computations in a single, cacheable entity.
   *
   * @param usages                  `ElementUsage`s found by the search method
   * @param didExitBeforeExhaustion Used by [[Search.Method.getUsages]] to determine validity of a cached result for
   *                                a given search.
   *
   *                                `false` if the total search space of a search method is guaranteed to be exhausted
   *                                and `usages` contains all usages that the search method is able to find,
   *                                regardless of which CanExit was provided to the pipeline.
   *
   *                                `true` otherwise.
   *
   *                                Typically a value of `false` coincides with `usages.isEmpty`, and vice versa, but
   *                                not necessarily. For such a counter-example see [[RefCountHolderSearch]].
   */
  final class SearchMethodResult(val usages: Usages, val didExitBeforeExhaustion: Boolean)

  /**
   * Encapsulates any context that may be relevant for search methods when searching for usages.
   *
   * @param element The element under inspection for which usages are to be found.
   * @param canExit The same `CanExit` that was provided by the cheapRefSearch consumer during pipeline construction.
   */
  final class Context(val element: ScNamedElement, val canExit: CanExit)

  /**
   * A `Search.Method` implementation is a method, approach or heuristic to search for usages. For example,
   * text-search is one method (see [[TextSearch]]), and true reference searching is another (see [[LocalRefSearch]]).
   */
  trait Method {

    /**
     * By providing a `ShouldProcess` instance to a `Search.Method` upon construction, a
     * cheapRefSearch consumer expresses under which conditions a `Search.Method` should run.
     *
     * For example, a search method that analyses local implicits should only process elements that are both local
     * and implicit.
     *
     * Contrary to the [[Search.Context]] passed to `searchForUsages`, `shouldProcess` is static over the lifetime of a
     * search method. It functions the same way, regardless of element under scrutiny and purpose of a cheapRefSearch
     * consumer.
     *
     * For concrete examples see [[SearchMethodsWithProjectBoundCache]].
     */
    val shouldProcess: ShouldProcess

    /**
     * The only method (as in "function") a `Search.Method` needs to implement, and it's where the `Search.Method`
     * performs the actual work of searching for usages.
     */
    protected def searchForUsages(ctx: Search.Context): SearchMethodResult

    private val lastModCount = new AtomicLong(0)

    /**
     * Multiple consumers of cheapRefSearch are expected to query usages of the exact same [[ScNamedElement]], so
     * we cache search method results to avoid unnecessary computation.
     *
     * Our cache needs to
     * 1. be thread-safe, because a typical consumer is an inspection, and inspections are run in parallel;
     * 2. store its keys as weak references, because the lifecycle of a PSI element is managed by the IntelliJ core.
     *
     * By using a WeakHashMap, we make sure that PSI elements that have become obsolete, for whatever reason, are
     * automagically removed from our cache.
     */
    private[Method] val cache: util.Map[ScNamedElement, SearchMethodResult] =
      java.util.Collections.synchronizedMap(new java.util.WeakHashMap[ScNamedElement, SearchMethodResult])

    /**
     * Returns usages of a given element.
     *
     * If results that satisfy the `Search.Context` are present in the cache, these will be returned.
     * If not, `searchForUsages` is invoked, and its results are stored in the cache and returned.
     */
    def getUsages(ctx: Search.Context): Usages = {

      def searchForAndCacheUsages(): Usages = {
        val res = searchForUsages(ctx)
        cache.put(ctx.element, res)
        res.usages
      }

      // Returns true if PSI tree has changed and cache was flushed
      def flushCacheIfPsiHasChanged(): Boolean = {
        val newModCount = anyScalaPsiChange.getModificationCount
        if (lastModCount.get() != newModCount) {
          cache.clear()
          lastModCount.set(newModCount)
          true
        } else {
          false
        }
      }

      val cacheWasFlushed = flushCacheIfPsiHasChanged()

      if (!cacheWasFlushed && cache.containsKey(ctx.element)) {

        val cachedComputeResult = cache.get(ctx.element)
        val canExit = cachedComputeResult.usages.exists(ctx.canExit)

        if (!canExit && cachedComputeResult.didExitBeforeExhaustion) {
          searchForAndCacheUsages()
        } else {
          cachedComputeResult.usages
        }
      } else {
        searchForAndCacheUsages()
      }
    }
  }

  private[declarationRedundancy] final object Pipeline {

    /**
     * The conditions that are at `ShouldProcess`'s disposal when deciding if the `Search.Method` it belongs to
     * should be run as part of a given pipeline execution, or not.
     *
     * Also see [[Pipeline.runSearchPipeline]]
     */
    class Conditions(
      val isOnTheFly: Boolean,
      val isOnlyVisibleInLocalFile: Boolean,
      val isImplicit: Boolean,
      val isMemberOfUnusedTypeDefinition: Boolean,
      val isScalaClass: Boolean
    )

    /**
     * See [[Method.shouldProcess]]
     */
    type ShouldProcess = Conditions => Boolean
  }

  private[declarationRedundancy] final object Util {

    /**
     * PSI trees are complex, layered structures, where each node is a `PsiElement`.
     *
     * For example, a Scala source file with a simple line of code like `val x = 42` and nothing else, already amounts
     * to a PSI tree with a depth of 5 layers. The `x` in this line of code inhabits 3 of those 5 layers. This means
     * that exhaustive traversal of the tree might result in an inspection being offered 3 different types of PSI
     * elements whose `getText` exactly matches the string "x". Since we are ultimately interested in highlighting a
     * specific text-range of some code, it is typically sufficient to inspect only one of those 3 elements.
     *
     * Moreover, the Scala PSI element implementations may not be as one expects. For example `new Foo` is parsed as a
     * `ScNewTemplateDefinition`, which inherits from `ScNamedElement`, but does not have a `nameId`.
     *
     * `shouldProcessElement` is in charge of filtering out elements that should not be considered when searching for
     * usages. It prunes the tree in order not to subject search methods to nonsensical or semantically duplicate
     * searches.
     *
     * It is important to note that only [[ScNamedElement]]s are considered for further processing, and only a very
     * specific subset of them.
     *
     * As you may guess, the below is the result of quite a bit of fine-tuning and tweaking over the years, so tread
     * with care when making changes, and regularly run the full `declarationRedundancy` test suite locally to catch
     * failures early on. Add negative and positive tests to cover your changes, or chances are that your shiny new
     * feature or bugfix will be broken the next time someone makes changes here.
     */
    def shouldProcessElement(element: PsiElement): Boolean = {

      def hasOverrideModifier(member: ScModifierListOwner): Boolean =
        member.hasModifierPropertyScala(ScalaModifier.OVERRIDE)

      def isOverridingOrOverridden(element: PsiNamedElement): Boolean =
        superValsSignatures(element, withSelfType = true).nonEmpty || isOverridden(element) ||
          superTypeSignatures(element).nonEmpty

      def isOverridingFunction(func: ScFunction): Boolean =
        hasOverrideModifier(func) || func.superSignatures.nonEmpty || isOverridden(func)

      def isOverridden(member: PsiNamedElement): Boolean =
        ScalaOverridingMemberSearcher.search(member, deep = false, withSelfType = true).nonEmpty

      val onlyVisibleInLocalFile = isOnlyVisibleInLocalFile(element)

      element match {
        case e if !onlyVisibleInLocalFile && TestSourcesFilter.isTestSources(e.getContainingFile.getVirtualFile, e.getProject) => false
        case _: ScSelfTypeElement => false
        case e: ScalaPsiElement if e.module.exists(_.isBuildModule) => false
        case e: PsiElement if UnusedDeclarationInspectionBase.isDeclaredAsEntryPoint(e) => false
        case obj: ScObject if ScalaMainMethodUtil.hasScala2MainMethod(obj) => false
        case n: ScNamedElement if n.nameId == null || n.name == "_" || isOverridingOrOverridden(n) => false
        case n: ScNamedElement =>
          n match {
            case p: ScModifierListOwner if hasOverrideModifier(p) => false
            case fd: ScFunctionDefinition if ScalaMainMethodUtil.isMainMethod(fd) => false
            case f: ScFunction if f.isSpecial || isOverridingFunction(f) || f.isConstructor => false
            case p: ScClassParameter if p.isCaseClassVal || p.isEnumVal || p.isEnumCaseVal || isImplicit(p.containingClass) => false
            case m: ScMember if m.getContext.is[ScRefinement] => false
            case p: ScParameter =>
              p.parent.flatMap(_.parent.flatMap(_.parent)) match {
                case Some(_: ScFunctionDeclaration) => false
                case Some(f: ScFunctionDefinition) if ScalaOverridingMemberSearcher.search(f).nonEmpty ||
                  isOverridingFunction(f) || ScalaMainMethodUtil.isMainMethod(f) => false
                case _ => !ScalaPsiUtil.isImplicit(n) || (onlyVisibleInLocalFile &&
                  p.typeElement.forall(!_.getText.contains("DummyImplicit")))
              }
            case _ => !ScalaPsiUtil.isImplicit(n) || onlyVisibleInLocalFile
          }
        case _ => false
      }
    }
  }
}
