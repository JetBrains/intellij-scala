package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{LocalSearchScope, PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiIdentifier, PsiNamedElement}
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiFileExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isImplicit, isOnlyVisibleInLocalFile, superValsSignatures}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunction, ScFunctionDeclaration, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.ImplicitUtil.ImplicitTargetExt
import org.jetbrains.plugins.scala.util.{ScalaMainMethodUtil, ScalaUsageNamesUtil}

import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * A cheap reference searcher, suitable for on-the-fly inspections.
 *
 * Rationale:
 * Multiple on-the-fly inspections require fast reference searching. The specific case
 * that led to the creation of the file you're looking at is
 * [[org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection]],
 * which turned out to require exactly the same heuristics as
 * [[org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspection]],
 * from which the search algorithm below was extracted, and now shared between these two inspections.
 *
 * Contract:
 * 1. It promises not to miss references, but there may be false positives.
 *
 * 2. Any search approach that can stop before having discovered all potential references,
 * like text-search, will indeed stop after a reference outside the target element's
 * private scope has been found.
 *
 * 3. Some references are only registered to exist, with no further information about them.
 */

object CheapRefSearcher {

  sealed trait ElementUsage {
    val targetCanBePrivate: Boolean
  }

  case object UnknownElementUsage extends ElementUsage {
    override val targetCanBePrivate: Boolean = false
  }

  case class KnownElementUsage(reference: PsiElement, target: ScNamedElement) extends ElementUsage {
    override lazy val targetCanBePrivate: Boolean = {
      val targetContainingClass = PsiTreeUtil.getParentOfType(target, classOf[PsiClass])
      var refContainingClass = PsiTreeUtil.getParentOfType(reference, classOf[PsiClass])

      val MaxSearchDepth = 10
      var counter = 0

      if (targetContainingClass == null) false else {
        while (counter < MaxSearchDepth && refContainingClass != null && refContainingClass != targetContainingClass) {
          refContainingClass = PsiTreeUtil.getParentOfType(refContainingClass, classOf[PsiClass])
          counter += 1
        }

        refContainingClass == targetContainingClass
      }
    }
  }

  def search(element: ScNamedElement, isOnTheFly: Boolean, reportPublicDeclarations: Boolean): Seq[ElementUsage] =
    if (!shouldProcessElement(element)) Seq(UnknownElementUsage) else {
      lazy val refSearch = referencesSearch(element)
      lazy val enumSearch = getForeignEnumUsages(element)

      if (isOnlyVisibleInLocalFile(element)) {
        if (isImplicit(element)) {
          isImplicitUsed(element)
        } else if (isOnTheFly) {
          val refCounter = ScalaRefCountHolder(element)
          var used = false
          val success = refCounter.runIfUnusedReferencesInfoIsAlreadyRetrievedOrSkip { () =>
            used = refCounter.isValueReadUsed(element) || refCounter.isValueWriteUsed(element)
          }
          if (!success || used) Seq(UnknownElementUsage) else Seq.empty
        } else {
          refSearch
        }
      } else if (refSearch.nonEmpty) {
        refSearch
      } else if (!reportPublicDeclarations) {
        Seq(UnknownElementUsage)
      } else if (enumSearch.nonEmpty) {
        enumSearch
      } else if (ScalaPsiUtil.isImplicit(element)) {
        Seq(UnknownElementUsage)
      } else {
        textSearch(element)
      }
    }

  private def referencesSearch(element: ScNamedElement): Seq[ElementUsage] = {
    val elementsForSearch = element match {
      case enumCase: ScEnumCase =>
        val syntheticMembers = ScalaPsiUtil.getCompanionModule(enumCase.enumParent).toSeq.flatMap(_.membersWithSynthetic).collect {
          case n: ScNamedElement if ScalaUsageNamesUtil.enumSyntheticMethodNames.contains(n.name) => n
        }
        enumCase.getSyntheticCounterpart +: syntheticMembers
      case e: ScNamedElement => Seq(e)
    }

    val scope = new LocalSearchScope(element.getContainingFile)

    elementsForSearch.flatMap(ReferencesSearch.search(_, scope).findAll().asScala).map { ref =>
      KnownElementUsage(ref.getElement, element)
    }
  }

  private def getForeignEnumUsages(element: ScNamedElement): Seq[ElementUsage] = {

    val result = new mutable.ListBuffer[ElementUsage]

    val scEnum = element match {
      case el: ScEnumCase => Some(el.enumParent)
      case el: ScEnum => Some(el)
      case _ => None
    }

    val processor = new TextOccurenceProcessor {
      override def execute(e2: PsiElement, offsetInElement: Int): Boolean =
        if (e2.getContainingFile.isScala2File || e2.getContainingFile.isScala3File) {
          true
        } else {
          result.addOne(KnownElementUsage(e2, element))
          result.last.targetCanBePrivate
        }
    }

    val caseSensitive = true
    val searchContext = (UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES).toShort
    val scope = element.getUseScope

    scEnum.foreach { enum =>
      PsiSearchHelper.getInstance(element.getProject).processElementsWithWord(processor, scope, enum.name, searchContext, caseSensitive)

      if (result.isEmpty) {
        PsiSearchHelper.getInstance(element.getProject)
          .processElementsWithWord(processor, scope, s"${enum.name}$$.MODULE$$", searchContext, caseSensitive)
      }
    }

    result.toSeq
  }

  private def textSearch(element: ScNamedElement): Seq[ElementUsage] = {
    val result = new mutable.ListBuffer[ElementUsage]
    val helper = PsiSearchHelper.getInstance(element.getProject)
    val processor = new TextOccurenceProcessor {
      override def execute(e2: PsiElement, offsetInElement: Int): Boolean =
        if (element.getContainingFile == e2.getContainingFile) {
          true
        } else {
          (e2, Option(e2.getParent)) match {
            case (_, Some(_: ScReferencePattern)) =>
            case (_, Some(_: ScTypeDefinition)) =>
            case (_: PsiIdentifier, _) => result.addOne(KnownElementUsage(e2, element))
            case (l: LeafPsiElement, _) if l.isIdentifier => result.addOne(KnownElementUsage(e2, element))
            case (_: ScStableCodeReference, _) => result.addOne(KnownElementUsage(e2, element))
            case _ =>
          }

          result.isEmpty || result.last.targetCanBePrivate
        }
    }

    ScalaUsageNamesUtil.getStringsToSearch(element).asScala.foreach { name =>
      if (result.isEmpty) {
        helper.processElementsWithWord(processor, element.getUseScope, name,
          (UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES).toShort, true)
      }
    }

    result.toSeq
  }

  private def isImplicitUsed(target: PsiElement): Seq[ElementUsage] =
    target.getContainingFile.depthFirst().find(target.refOrImplicitRefIn(_).nonEmpty).toSeq.map(_ => UnknownElementUsage)

  private def shouldProcessElement(element: PsiElement): Boolean = {

    def hasOverrideModifier(member: ScModifierListOwner): Boolean =
      member.hasModifierPropertyScala(ScalaModifier.OVERRIDE)

    def isOverridingOrOverridden(element: PsiNamedElement): Boolean =
      superValsSignatures(element, withSelfType = true).nonEmpty || isOverridden(element)

    def isOverridingFunction(func: ScFunction): Boolean =
      hasOverrideModifier(func) || func.superSignatures.nonEmpty || isOverridden(func)

    def isOverridden(member: PsiNamedElement): Boolean =
      ScalaOverridingMemberSearcher.search(member, deep = false, withSelfType = true).nonEmpty

    element match {
      case e if !isOnlyVisibleInLocalFile(e) && TestSourcesFilter.isTestSources(e.getContainingFile.getVirtualFile, e.getProject) => false
      case _: ScSelfTypeElement => false
      case e: ScalaPsiElement if e.module.exists(_.isBuildModule) => false
      case e: PsiElement if UnusedDeclarationInspectionBase.isDeclaredAsEntryPoint(e) => false
      case obj: ScObject if ScalaMainMethodUtil.hasScala2MainMethod(obj) => false
      case n: ScNamedElement if ScalaPsiUtil.isImplicit(n) => isOnlyVisibleInLocalFile(n)
      case n: ScNamedElement if n.nameId == null || n.name == "_" || isOverridingOrOverridden(n) => false
      case n: ScNamedElement =>
        n match {
          case p: ScModifierListOwner if hasOverrideModifier(p) => false
          case fd: ScFunctionDefinition if ScalaMainMethodUtil.isMainMethod(fd) => false
          case f: ScFunction if f.isSpecial || isOverridingFunction(f) || f.isConstructor => false
          case p: ScClassParameter if p.isCaseClassVal || p.isEnumVal || p.isEnumCaseVal || isImplicit(p.containingClass) => false
          case p: ScParameter =>
            p.parent.flatMap(_.parent.flatMap(_.parent)) match {
              case Some(_: ScFunctionDeclaration) => false
              case Some(f: ScFunctionDefinition) if ScalaOverridingMemberSearcher.search(f).nonEmpty ||
                isOverridingFunction(f) || ScalaMainMethodUtil.isMainMethod(f) => false
              case _ => true
            }
          case _ => true
        }
      case _ => false
    }
  }
}
