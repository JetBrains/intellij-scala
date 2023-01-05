package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeInsight.intention.PriorityAction.Priority
import com.intellij.codeInsight.intention.{FileModifier, PriorityAction}
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection.getPipeline
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.SymbolEscaping.elementIsSymbolWhichEscapesItsDefiningScopeWhenItIsPrivate
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.{ElementUsage, Search, SearchMethodsWithProjectBoundCache}
import org.jetbrains.plugins.scala.codeInspection.typeAnnotation.TypeAnnotationInspection
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiModifierListOwnerExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isLocalClass
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods.isBeanProperty
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}

import scala.annotation.tailrec

final class ScalaAccessCanBeTightenedInspection extends HighlightingPassInspection {

  private def getProblemInfos(element: ScNamedElement,
                              modifierListOwner: ScModifierListOwner,
                              isOnTheFly: Boolean): Seq[ProblemInfo] =
    if (Search.Util.shouldProcessElement(element)) {

      val usages = getPipeline(element.getProject).runSearchPipeline(element, isOnTheFly)

      if (usages.nonEmpty && usages.forall(_.targetCanBePrivate) &&
          !elementIsSymbolWhichEscapesItsDefiningScopeWhenItIsPrivate(element)) {

        val fix = new ScalaAccessCanBeTightenedInspection.MakePrivateQuickFix(modifierListOwner)

        Seq(
          ProblemInfo(
            element.nameId,
            ScalaInspectionBundle.message("access.can.be.private"),
            Seq(fix)
          )
        )
      } else Seq.empty
    } else Seq.empty

  override def invoke(element: PsiElement, isOnTheFly: Boolean): Seq[ProblemInfo] =
    element match {
      case n: ScNamedElement with ScModifierListOwner if !n.hasModifierPropertyScala("private") =>
        n match {
          case _: ScFunctionDefinition | _: ScTypeDefinition =>
            getProblemInfos(n, n, isOnTheFly)
          case _ => Seq.empty
        }
      case patternList@ScPatternList(Seq(pattern: ScReferencePattern)) =>
        val modifierListOwner = patternList.getParent.asInstanceOf[ScModifierListOwner]
        if (!modifierListOwner.hasModifierPropertyScala("private")) {
          getProblemInfos(pattern, modifierListOwner, isOnTheFly)
        } else {
          Seq.empty
        }
      case _ => Seq.empty
    }

  @tailrec
  override def shouldProcessElement(element: PsiElement): Boolean = {

    def isTypeDefThatShouldNotBeInspected(t: ScTypeDefinition): Boolean =
      (t.getContainingFile.asOptionOf[ScalaFile].exists(_.isWorksheetFile) && t.isTopLevel) || t.isPackageObject

    element match {
      case t: ScTypeDefinition if isTypeDefThatShouldNotBeInspected(t) => false
      case m: ScMember => !m.isLocal && !Option(m.containingClass).exists(isLocalClass) && !isBeanProperty(m)
      case p: ScPatternList => shouldProcessElement(p.getContext)
      case _ => true
    }
  }
}

private object ScalaAccessCanBeTightenedInspection {

  private[declarationRedundancy]
  class MakePrivateQuickFix(element: ScModifierListOwner)
    extends LocalQuickFixAndIntentionActionOnPsiElement(element)
      with PriorityAction {

    private lazy val priority = quickFixPriority(element)

    override def getPriority: PriorityAction.Priority = priority

    override def getText: String = ScalaInspectionBundle.message("make.private")

    override def getFamilyName: String = ScalaInspectionBundle.message("change.modifier")

    override def invoke(project: Project, file: PsiFile, editor: Editor, startElement: PsiElement, endElement: PsiElement): Unit =
      element.setModifierProperty("private")

    override def getFileModifierForPreview(target: PsiFile): FileModifier =
      new MakePrivateQuickFix(PsiTreeUtil.findSameElementInCopy(element, target))
  }

  private def getPipeline(project: Project): Pipeline = {

    val canExit = (usage: ElementUsage) => !usage.targetCanBePrivate

    val searcher = SearchMethodsWithProjectBoundCache(project)

    val localSearch = searcher.LocalSearchMethods
    val globalSearch = searcher.GlobalSearchMethods

    new Pipeline(localSearch ++ globalSearch, canExit)
  }

  /**
   * When type annotation warning is shown we want to show "Make private" quick fix above "Add type annotation".
   * This is because after adding `private` modifier "missing type annotation" warning will become non-actual
   * (at least with the default code style settings)
   *
   * So, in case a [[ScalaAccessCanBeTightenedInspection.MakePrivateQuickFix]]
   * is offered to the user, we essentially perform the same check as [[TypeAnnotationInspection]].
   * When the result is positive (i.e. a type annotation QuickFix is offered), we use a higher priority for "Make private" quick fix.
   *
   * @note At the moment [[PriorityAction]] is the best API we have to reorder actions & quick fixes<br>
   *       See [[https://youtrack.jetbrains.com/issue/IDEA-88512]].
   * @note [[TypeAnnotationInspection]] doesn't implement [[PriorityAction]] so its priority is by default NORMAL<br>
   *       This is because it will be wrapped into [[com.intellij.codeInspection.ex.QuickFixWrapper]].
   */
  private def quickFixPriority(modifierListOwner: ScModifierListOwner): Priority = {
    val needHigherPriority = typeAnnotationWarningWillBeShown(modifierListOwner)
    if (needHigherPriority)
      Priority.HIGH
    else
      Priority.NORMAL
  }

  private def typeAnnotationWarningWillBeShown(modifierListOwner: ScModifierListOwner): Boolean = {
    val inspectionProfile = InspectionProjectProfileManager.getInstance(modifierListOwner.getProject).getCurrentProfile
    //TypeAnnotationToolKey can be null in tests where the inspection is not explicitly enabled
    val typeAnnotationKey = TypeAnnotationInspection.highlightKey
    if (typeAnnotationKey != null && !inspectionProfile.isToolEnabled(typeAnnotationKey))
      return false

    val (expression, hasExplicitType) = modifierListOwner match {
      case value: ScPatternDefinition if value.isSimple =>
        (value.expr, value.hasExplicitType)
      case method: ScFunctionDefinition if method.hasAssign && !method.isConstructor =>
        (method.body, method.hasExplicitType)
      case _ =>
        (None, true)
    }

    !hasExplicitType && {
      val typeAnnotationReason = TypeAnnotationInspection.getReasonForTypeAnnotationOn(modifierListOwner, expression)
      typeAnnotationReason.isDefined
    }
  }
}