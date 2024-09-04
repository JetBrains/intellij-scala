package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.AbstractIntentionAction
import com.intellij.codeInsight.navigation.NavigationUtil.getPsiElementPopup
import com.intellij.ide.util.{PsiClassListCellRenderer, PsiElementListCellRenderer}
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.command.{CommandProcessor, WriteCommandAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.scala.{ScalaBundle, isUnitTestMode}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier.{ABSTRACT, OVERRIDE}
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.refactoring.extractTrait.ScalaExtractMemberInfo
import org.jetbrains.plugins.scala.lang.refactoring.memberPullUp.ScalaPullUpProcessor

final class PullUpQuickFix(member: ScMember, name: String) extends AbstractIntentionAction {

  import PullUpQuickFix._

  private val smartPointer = member.createSmartPointer

  override val getText: String = member match {
    case _: ScVariableDefinition => ScalaBundle.message("pull.variable.to", name)
    case _: ScPatternDefinition => ScalaBundle.message("pull.value.to", name)
    case _ => ScalaBundle.message("pull.method.to", name)
  }

  override def startInWriteAction: Boolean = true

  override def isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean =
    selectedMemberWithContainingClass.exists {
      case PullUpExecutor(memberToOverride, sourceClass) =>
        memberToOverride.hasModifierProperty(OVERRIDE) && applicableScalaSupers(sourceClass).nonEmpty
    }

  override def invoke(project: Project, editor: Editor, psiFile: PsiFile): Unit = for {
    executor <- selectedMemberWithContainingClass
  } {
    implicit val p: Project = project
    val superClasses = applicableScalaSupers(executor.sourceClass)

    if (isUnitTestMode) {
      superClasses.nextOption().foreach {
        executor(_)
      }
    } else {
      getPsiElementPopup(
        superClasses.toArray,
        (new PsiClassListCellRenderer).asInstanceOf[PsiElementListCellRenderer[ScTypeDefinition]],
        JavaBundle.message("choose.class"),
        new PullUpProcessor(executor)
      ).showInBestPositionFor(editor)
    }
  }

  private def selectedMemberWithContainingClass: Option[PullUpExecutor] = smartPointer match {
    case ValidSmartPointer(memberToOverride@ContainingClass(sourceClass: ScTypeDefinition)) => Some(PullUpExecutor(memberToOverride, sourceClass))
    case _ => None
  }
}

object PullUpQuickFix {

  private case class PullUpExecutor(memberToOverride: ScMember,
                                    sourceClass: ScTypeDefinition) {

    @RequiresEdt
    def apply(targetClass: ScTypeDefinition)(implicit project: Project): Unit = {
      val executeRunnable: Runnable = () => execute(targetClass, project)
      val withAlternativeResolve: ThrowableRunnable[RuntimeException] =
        () => DumbService.getInstance(project).withAlternativeResolveEnabled(executeRunnable)

      if (Registry.is("run.refactorings.under.progress")) {
        val commandName = CommandProcessor.getInstance().getCurrentCommandName
        val title = ScalaBundle.message("pulling.member.to.supertype.progress.title")

        val performUnderProgress: java.util.function.Consumer[ProgressIndicator] = { indicator =>
          indicator.setIndeterminate(false)
          indicator.setFraction(0)
          withAlternativeResolve.run()
        }

        val writeActionRunnable: Runnable = { () =>
          //noinspection ApiStatus
          ApplicationManagerEx.getApplicationEx.runWriteActionWithCancellableProgressInDispatchThread(
            title, project, null, performUnderProgress)
        }
        if (commandName eq null) {
          CommandProcessor.getInstance().executeCommand(project, writeActionRunnable, title, null)
        } else {
          writeActionRunnable.run()
        }
      } else {
        WriteCommandAction.writeCommandAction(project, targetClass.getContainingFile).run(withAlternativeResolve)
      }
    }

    private def execute(targetClass: ScTypeDefinition, project: Project): Unit = {
      val info = new ScalaExtractMemberInfo(memberToOverride)
      info.setToAbstract(true)
      val typeAdjuster = new TypeAdjuster()

      new ScalaPullUpProcessor(
        project,
        sourceClass,
        targetClass,
        Seq(info)
      ).moveMembersToBase(typeAdjuster)

      typeAdjuster.adjustTypes()
    }
  }

  private def applicableScalaSupers(sourceClass: ScTypeDefinition) = for {
    superClass <- sourceClass.allSupers.iterator
    if superClass.isInstanceOf[ScTypeDefinition] &&
      superClass.hasModifierProperty(ABSTRACT) &&
      superClass.containingVirtualFile.exists(_.isWritable)
  } yield superClass.asInstanceOf[ScTypeDefinition]

  private final class PullUpProcessor(executor: PullUpExecutor)
                                     (implicit project: Project)
    extends PsiElementProcessor[ScTypeDefinition] {

    override def execute(targetClass: ScTypeDefinition): Boolean = {
      invokeLater {
        inWriteCommandAction {
          executor(targetClass)
        }
      }
      true
    }
  }
}
