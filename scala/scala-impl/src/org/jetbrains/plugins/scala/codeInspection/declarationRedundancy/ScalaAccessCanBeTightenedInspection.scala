package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.{LocalQuickFixOnPsiElement, ProblemHighlightType}
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection.getPipeline
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.{ElementUsage, Search, SearchMethodsWithProjectBoundCache}
import org.jetbrains.plugins.scala.codeInspection.typeAnnotation.TypeAnnotationInspection
import org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt
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

      if (usages.forall(_.targetCanBePrivate)) {
        val fix = new ScalaAccessCanBeTightenedInspection.MakePrivateQuickFix(modifierListOwner)

        Seq(
          ProblemInfo(
            element.nameId,
            ScalaInspectionBundle.message("access.can.be.private"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
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
  override def shouldProcessElement(element: PsiElement): Boolean =
    element match {
      case m: ScMember => !m.isLocal
      case p: ScPatternList => shouldProcessElement(p.getContext)
      case _ => true
    }
}

private object ScalaAccessCanBeTightenedInspection {

  private[declarationRedundancy] class MakePrivateQuickFix(element: ScModifierListOwner) extends LocalQuickFixOnPsiElement(element) {

    private val text = quickFixText(element)

    override def getText: String = text

    override def getFamilyName: String = ScalaInspectionBundle.message("change.modifier")

    override def invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Unit =
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
   * The reason for the below logic is as follows:
   *
   * 1. On the one hand we want to keep our QuickFix text congruent with Java's
   * [[com.intellij.codeInspection.visibility.AccessCanBeTightenedInspection]], where the QuickFix text
   * is "Make 'private'".
   * 2. On the other hand we prefer to present the QuickFix that adds the 'private'
   * modifier at the top of the QuickFix list.
   * 3. Since the platform applies alphabetical ordering when presenting the QuickFixes,
   * and quite often [[org.jetbrains.plugins.scala.codeInspection.typeAnnotation.TypeAnnotationInspection]]
   * offers a "Add type annotation" QuickFix at the same time that a declaration can be made private,
   * a QuickFix with the text "Make 'private'" would not appear at the top in such a case.
   *
   * So, in case a [[org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection.MakePrivateQuickFix]] is
   * offered to the user, we essentially perform the same check as
   * [[org.jetbrains.plugins.scala.codeInspection.typeAnnotation.TypeAnnotationInspection]]. When the result is
   * positive (i.e. a type annotation QuickFix is offered), we go for a fallback text that starts with "Add ...".
   *
   * In case we ever get custom QuickFix ordering, this fallback routine becomes redundant.
   * See [[https://youtrack.jetbrains.com/issue/IDEA-88512]].
   */
  @Nls
  private def quickFixText(modifierListOwner: ScModifierListOwner): String = {
    val expression = modifierListOwner match {
      case value: ScPatternDefinition if value.isSimple && !value.hasExplicitType =>
        value.expr
      case method: ScFunctionDefinition if method.hasAssign && !method.hasExplicitType && !method.isConstructor =>
        method.body
      case _ => None
    }

    if (TypeAnnotationInspection.getReasonForTypeAnnotationOn(modifierListOwner, expression).isEmpty) {
      ScalaInspectionBundle.message("make.private")
    } else {
      ScalaInspectionBundle.message("add.private.modifier")
    }
  }
}