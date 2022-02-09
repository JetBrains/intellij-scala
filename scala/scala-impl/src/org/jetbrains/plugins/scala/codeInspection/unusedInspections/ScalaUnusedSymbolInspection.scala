package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.ScalaUnusedSymbolInspection._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isOnlyVisibleInLocalFile, superValsSignatures}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerators, ScFunctionExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.util.SAMUtil.PsiClassToSAMExt
import org.jetbrains.plugins.scala.util.{ScalaMainMethodUtil, ScalaNamesUtil}

import scala.jdk.CollectionConverters._


class ScalaUnusedSymbolInspection extends HighlightingPassInspection {
  override def isEnabledByDefault: Boolean = true

  override def getDisplayName: String = ScalaInspectionBundle.message("display.name.unused.symbol")

  private def isElementUsed(element: ScNamedElement, isOnTheFly: Boolean): Boolean = {

    def isAssumedToBeUsed: Boolean =
      element match {
        case t: ScTypeDefinition if t.isSAMable => true
        case _ => false
      }

    if (isAssumedToBeUsed) {
      true
    } else if (isOnTheFly) {
      var used = false

      if (isOnlyVisibleInLocalFile(element)) {
        //we can trust RefCounter because references are counted during highlighting
        val refCounter = ScalaRefCountHolder(element)

        val success = refCounter.runIfUnusedReferencesInfoIsAlreadyRetrievedOrSkip { () =>
          used = refCounter.isValueReadUsed(element) || refCounter.isValueWriteUsed(element)
        }

        !success || used // Return true also if runIfUnused... was a failure
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

        ScalaNamesUtil.getNamesOf(element).asScala.foreach { name =>
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
      case scClass: ScClass => Seq(scClass)
      case fun: ScFunctionExpr => fun.parameters.filterNot(p => p.isWildcard || p.isImplicitParameter)
      case fun: ScMethodLike =>
        val funIsPublic = !fun.containingClass.toOption.exists(isOnlyVisibleInLocalFile)

        def nonPrivateClassMemberParam(param: ScParameter): Boolean =
          funIsPublic && param.asOptionOf[ScClassParameter].exists(p => p.isClassMember && (!p.isPrivate))

        def overridingParam(param: ScParameter): Boolean =
          param.asOptionOf[ScClassParameter].exists(isOverridingOrOverridden)

        def caseClassParam(param: ScParameter): Boolean =
          param.asOptionOf[ScClassParameter].exists(_.isCaseClassVal)

        fun +: fun.parameters
          .filterNot(_.isWildcard)
          .filter(_.isPhysical) // context bound are desugared into parameters, for example
          .filterNot(_.isImplicitParameter)
          .filterNot(caseClassParam)
          .filterNot(nonPrivateClassMemberParam)
          .filterNot(overridingParam)
      case caseClause: ScCaseClause => caseClause.pattern.toSeq.flatMap(_.bindings).filterNot(_.isWildcard)
      case declaredHolder: ScDeclaredElementsHolder => declaredHolder.declaredElements.filterNot(isOverridingOrOverridden)
      case enumerators: ScEnumerators => enumerators.patterns.flatMap(_.bindings).filterNot(_.isWildcard) //for statement
      case _ => Seq.empty
    }
    elements.flatMap {
      case holder: PsiAnnotationOwner if hasUnusedAnnotation(holder) => Seq.empty
      case named: ScNamedElement =>
        if (!isElementUsed(named, isOnTheFly)) {
          Seq(ProblemInfo(named.nameId, ScalaUnusedSymbolInspection.annotationDescription, ProblemHighlightType.LIKE_UNUSED_SYMBOL, DeleteUnusedElementFix.quickfixesFor(named)))
        } else Seq.empty
      case _ => Seq.empty
    }
  }

  override def shouldProcessElement(elem: PsiElement): Boolean = elem match {
    case e if !isUnitTestMode && e.isInScala3File => false // TODO Handle Scala 3 code (`enum case`s, etc.), SCL-19589
    case m: ScMember if m.hasModifierPropertyScala(ScalaKeyword.IMPLICIT) => false
    case p: ScModifierListOwner if hasOverrideModifier(p) => false
    case fd: ScFunctionDefinition if ScalaMainMethodUtil.isMainMethod(fd) => false
    case f: ScFunction if f.isSpecial || isOverridingFunction(f) => false
    case _ => true
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
