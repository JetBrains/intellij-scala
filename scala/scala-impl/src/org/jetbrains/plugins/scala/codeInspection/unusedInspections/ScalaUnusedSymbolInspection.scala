package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{LocalSearchScope, PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.ScalaUnusedSymbolInspection._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{inNameContext, isOnlyVisibleInLocalFile, superValsSignatures}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.util.SAMUtil.PsiClassToSAMExt
import org.jetbrains.plugins.scala.util.{ScalaMainMethodUtil, ScalaUsageNamesUtil}

import scala.jdk.CollectionConverters._

class ScalaUnusedSymbolInspection extends HighlightingPassInspection {
  override def isEnabledByDefault: Boolean = true

  override def getDisplayName: String = ScalaInspectionBundle.message("display.name.unused.symbol")

  private def isElementUsed(element: ScNamedElement, isOnTheFly: Boolean): Boolean = {
    if (isOnTheFly) {
      var used = false

      if (isOnlyVisibleInLocalFile(element)) {
        //we can trust RefCounter because references are counted during highlighting
        val refCounter = ScalaRefCountHolder(element)

        val success = refCounter.runIfUnusedReferencesInfoIsAlreadyRetrievedOrSkip { () =>
          used = refCounter.isValueReadUsed(element) || refCounter.isValueWriteUsed(element)
        }

        !success || used // Return true also if runIfUnused... was a failure
      } else if (ReferencesSearch.search(element, new LocalSearchScope(element.getContainingFile)).findFirst() != null) {
        true
      } else {
        val helper = PsiSearchHelper.getInstance(element.getProject)
        val processor = new TextOccurenceProcessor {
          override def execute(e2: PsiElement, offsetInElement: Int): Boolean = {
            inReadAction {
              if (element.getContainingFile == e2.getContainingFile) true else {
                used = true
                false
              }
            }
          }
        }

        ScalaUsageNamesUtil.getNamesOf(element).asScala.foreach { name =>
          if (!used) {
            helper.processElementsWithWord(
              processor,
              element.getUseScope,
              name,
              (UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES).toShort,
              true
            )
          }
        }
        used
      }
    } else {
      //need to look for references because file is not highlighted
      ReferencesSearch.search(element, element.getUseScope).findFirst() != null
    }
  }

  override def invoke(element: PsiElement, isOnTheFly: Boolean): Seq[ProblemInfo] = if (!shouldProcessElement(element)) Seq.empty else {
    val elements: Seq[PsiElement] = element match {
      case named: ScNamedElement => Seq(named)
      case _ => Seq.empty
    }
    elements.flatMap {
      case inNameContext(holder: PsiAnnotationOwner) if hasUnusedAnnotation(holder) => Seq.empty
      case named: ScNamedElement =>
        if (!isElementUsed(named, isOnTheFly)) {
          Seq(ProblemInfo(named.nameId, ScalaUnusedSymbolInspection.annotationDescription, ProblemHighlightType.LIKE_UNUSED_SYMBOL, DeleteUnusedElementFix.quickfixesFor(named)))
        } else Seq.empty
      case _ => Seq.empty
    }
  }

  override def shouldProcessElement(elem: PsiElement): Boolean = {
    elem match {
      case obj: ScObject if ScalaMainMethodUtil.hasScala2MainMethod(obj) => false
      case t: ScTypeDefinition if t.isSAMable => false
      case n: ScNamedElement if ScalaPsiUtil.isImplicit(n) || n.nameId == null || n.name == "_" || isOverridingOrOverridden(n) => false
      case n: ScNamedElement =>
        n match {
          case e if !isUnitTestMode && e.isInScala3File => false // TODO Handle Scala 3 code (`enum case`s, etc.), SCL-19589
          case p: ScModifierListOwner if hasOverrideModifier(p) => false
          case fd: ScFunctionDefinition if ScalaMainMethodUtil.isMainMethod(fd) => false
          case f: ScFunction if f.isSpecial || isOverridingFunction(f) => false
          case p: ScParameter =>
            p.parent.flatMap(_.parent.flatMap(_.parent)) match {
              case Some(f: ScFunctionDeclaration) if ScalaOverridingMemberSearcher.search(f).nonEmpty => false
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

object ScalaUnusedSymbolInspection {
  @Nls
  val annotationDescription: String = ScalaInspectionBundle.message("declaration.is.never.used")

  val shortName: String = "ScalaUnusedSymbol"

  private def hasOverrideModifier(member: ScModifierListOwner): Boolean =
    member.hasModifierPropertyScala(ScalaModifier.OVERRIDE)

  private def isOverridingOrOverridden(element: PsiNamedElement): Boolean =
    superValsSignatures(element, withSelfType = true).nonEmpty || isOverridden(element)

  private def isOverridingFunction(func: ScFunction): Boolean =
    hasOverrideModifier(func) || func.superSignatures.nonEmpty || isOverridden(func)

  private def isOverridden(member: PsiNamedElement): Boolean =
    ScalaOverridingMemberSearcher.search(member, deep = false, withSelfType = true).nonEmpty

  private def hasUnusedAnnotation(holder: PsiAnnotationOwner): Boolean =
    holder.hasAnnotation("scala.annotation.unused") ||
      // not entirely correct, but if we find @nowarn here in this situation
      // we can assume that it is directed at the unusedness of the symbol
      holder.hasAnnotation("scala.annotation.nowarn")
}
