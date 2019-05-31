package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.{PsiClass, PsiElement, PsiFile, SmartPsiElementPointer}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, PsiElementExt, inWriteCommandAction}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.refactoring.extractTrait.ScalaExtractMemberInfo
import org.jetbrains.plugins.scala.lang.refactoring.memberPullUp.ScalaPullUpProcessor

final class PullUpQuickFix(_member: ScMember, name: String) extends IntentionAction {
  private val smartPointer: SmartPsiElementPointer[ScMember] = _member.createSmartPointer

  override val getText: String = _member match {
    case _: ScVariableDefinition => ScalaBundle.message("pull.variable.to", name)
    case _: ScPatternDefinition => ScalaBundle.message("pull.value.to", name)
    case _ => ScalaBundle.message("pull.method.to", name)
  }
  override val getFamilyName: String = getText
  override val startInWriteAction: Boolean = true

  override def isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean = {
    selectedMember.zip(sourceClass).exists {
      case (member, clazz) =>
        member.hasModifierProperty("override") && clazz.allSupers.exists(isInWritableFile)
    }
  }

  override def invoke(project: Project, editor: Editor, psiFile: PsiFile): Unit = {
    for {
      member      <- selectedMember
      sourceClass <- sourceClass
    } {
      val superClasses = allSupers(project, sourceClass)

      if (!ApplicationManager.getApplication.isUnitTestMode) {
        NavigationUtil
          .getPsiElementPopup(
            superClasses.toArray,
            new PsiClassListCellRenderer,
            "Choose class",
            new PullUpProcessor(member, sourceClass, project))
          .showInBestPositionFor(editor)
      } else {
        // for headless test flow
        superClasses.headOption.foreach(pullUpMember(member, sourceClass, _: PsiClass, project))
      }
    }
  }

  private def allSupers(project: Project, clazz: PsiClass): Seq[PsiClass] =
    clazz.allSupers.filter(isInWritableFile)

  private def selectedMember: Option[ScMember] =
    Option(smartPointer.getElement)

  private def sourceClass: Option[ScTemplateDefinition] =
    selectedMember.flatMap(_.containingClass.toOption)

  private def pullUpMember(target: ScMember, from: ScTemplateDefinition, to: PsiClass, project: Project): Unit = {
    val info = new ScalaExtractMemberInfo(target)
    info.setToAbstract(true)
    new ScalaPullUpProcessor(project, from, to.asInstanceOf[ScTemplateDefinition], Seq(info))
      .moveMembersToBase()
  }

  private def isInWritableFile(element: PsiElement): Boolean =
    element.containingFile.flatMap(_.getVirtualFile.toOption).exists(_.isWritable)

  private class PullUpProcessor(memberToOverride: ScMember, sourceClass: ScTemplateDefinition, project: Project)
    extends PsiElementProcessor[PsiClass] {

    override def execute(t: PsiClass): Boolean = {
      ApplicationManager.getApplication.invokeLater(
        () => {
          inWriteCommandAction {
            pullUpMember(
              target = memberToOverride,
              from = sourceClass,
              to = t,
              project = project
            )
          }(project)
        })
      true
    }
  }
}
