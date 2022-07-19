package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFixOnPsiElement, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiElementVisitor, PsiFile}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.typeAnnotation.TypeAnnotationInspection
import org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}

private[declarationRedundancy] final class ScalaAccessCanBeTightenedInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    new PsiElementVisitor {
      override def visitElement(element: PsiElement): Unit = {
        (element, element) match {
          case (namedElement: ScNamedElement, modifierListOwner: ScModifierListOwner)
            if !modifierListOwner.hasModifierPropertyScala("private") =>
            namedElement match {
              case _: ScFunctionDefinition | _: ScTypeDefinition =>
                processElement(namedElement, modifierListOwner, holder, isOnTheFly)
              case _ =>
            }
          case (refPat: ScReferencePattern, _) =>
            val patternList = refPat.getParent.asInstanceOf[ScPatternList]
            if (patternList.patterns.size == 1) {
              val modifierListOwner = patternList.getParent.asInstanceOf[ScModifierListOwner]
              if (!modifierListOwner.hasModifierPropertyScala("private")) {
                processElement(refPat, modifierListOwner, holder, isOnTheFly)
              }
            }
          case _ =>
        }
      }
    }

  private def processElement(
    element: ScNamedElement,
    modifierListOwner: ScModifierListOwner,
    problemsHolder: ProblemsHolder,
    isOnTheFly: Boolean
  ): Unit = {

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
     * So, in case a [[org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.MakePrivateQuickFix]] is
     * offered to the user, we essentially perform the same check as
     * [[org.jetbrains.plugins.scala.codeInspection.typeAnnotation.TypeAnnotationInspection]]. When the result is
     * positive (i.e. a type annotation QuickFix is offered), we go for a fallback text that starts with "Add ...".
     *
     * In case we ever get custom QuickFix ordering, this fallback routine becomes redundant.
     * See [[https://youtrack.jetbrains.com/issue/IDEA-88512]].
     */
    @Nls
    lazy val quickFixText: String = {
      val expression = modifierListOwner match {
        case value: ScPatternDefinition if value.isSimple && !value.hasExplicitType =>
          value.expr
        case method: ScFunctionDefinition if method.hasAssign && !method.hasExplicitType && !method.isConstructor =>
          method.body
        case _ => None
      }

      if (TypeAnnotationInspection.getReasonForTypeAnnotationOn(modifierListOwner, expression).isEmpty) {
        "Make 'private'"
      } else {
        "Add 'private' modifier"
      }
    }

    if (element.getUsages(isOnTheFly, reportPublicDeclarations = true).forall(_.targetCanBePrivate)) {
      val fix = new MakePrivateQuickFix(modifierListOwner, quickFixText)
      problemsHolder.registerProblem(element.nameId, "Access can be private", ProblemHighlightType.WARNING, fix)
    }
  }
}

class MakePrivateQuickFix(element: ScModifierListOwner, @Nls text: String) extends LocalQuickFixOnPsiElement(element) {

  override def invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Unit =
    element.setModifierProperty("private")

  override def getText: String = text

  override def getFamilyName: String = "Change modifier"
}
