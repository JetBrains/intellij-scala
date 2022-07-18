package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFixOnPsiElement, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiElementVisitor, PsiFile}
import org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
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
    if (CheapRefSearcher.search(element, isOnTheFly, reportPublicDeclarations = true).forall(_.targetCanBePrivate)) {
      val fix = new MakePrivateQuickFix(modifierListOwner)
      problemsHolder.registerProblem(element.nameId, "Access can be private", ProblemHighlightType.WARNING, fix)
    }
  }
}

private class MakePrivateQuickFix(element: ScModifierListOwner) extends LocalQuickFixOnPsiElement(element) {

  override def invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Unit =
    element.setModifierProperty("private")

  override def getText: String = "Make 'private'"

  override def getFamilyName: String = "Change modifier"
}
