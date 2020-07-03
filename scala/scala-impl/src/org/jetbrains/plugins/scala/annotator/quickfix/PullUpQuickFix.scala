package org.jetbrains.plugins.scala
package annotator
package quickfix

import com.intellij.codeInsight.intention.AbstractIntentionAction
import com.intellij.codeInsight.navigation.NavigationUtil.getPsiElementPopup
import com.intellij.ide.util.{PsiClassListCellRenderer, PsiElementListCellRenderer}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.PsiElementProcessor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier.{ABSTRACT, OVERRIDE}
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
      superClasses.headOption.foreach {
        executor(_)
      }
    } else {
      getPsiElementPopup(
        superClasses.toArray,
        (new PsiClassListCellRenderer).asInstanceOf[PsiElementListCellRenderer[ScTypeDefinition]],
        ScalaBundle.message("choose.class"),
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

    def apply(targetClass: ScTypeDefinition)
             (implicit project: Project): Unit = {
      val info = new ScalaExtractMemberInfo(memberToOverride)
      info.setToAbstract(true)

      new ScalaPullUpProcessor(
        project,
        sourceClass,
        targetClass,
        Seq(info)
      ).moveMembersToBase()
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
