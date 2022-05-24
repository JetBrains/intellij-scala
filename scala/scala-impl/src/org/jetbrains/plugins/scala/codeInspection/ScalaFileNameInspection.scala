package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.codeInspection._
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiFile, PsiNamedElement}
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
  * User: Alexander Podkhalyuzin
  * Date: 02.07.2009
  */
final class ScalaFileNameInspection extends LocalInspectionTool {

  import ProblemDescriptor.EMPTY_ARRAY
  import ScalaFileNameInspection._

  override def checkFile(file: PsiFile,
                         manager: InspectionManager,
                         isOnTheFly: Boolean): Array[ProblemDescriptor] = file match {
    case scalaFile: ScalaFile if !ScalaLanguageConsole.isScalaConsoleFile(scalaFile) &&
      IntentionAvailabilityChecker.checkInspection(this, scalaFile) &&
      !InjectedLanguageManager.getInstance(scalaFile.getProject).isInjectedFragment(scalaFile) &&
      !scalaFile.isScriptFile &&
      !scalaFile.isWorksheetFile &&
      //if ScalaProjectSettings.TREAT_SCRATCH_AS_WORKSHEET == false
      //isWorksheetFile also returns false
      //but we do not want to handle scratch files anyway
      !Option(scalaFile.getVirtualFile).exists(ScratchUtil.isScratch) =>

      val virtualFileName = scalaFile.getVirtualFile match {
        case null => return EMPTY_ARRAY
        case virtualFile => virtualFile.getNameWithoutExtension
      }

      val maybeDescriptors = scalaFile.typeDefinitions match {
        case Seq(_, _, _, _*) => None
        case Seq(first, second) if first.name != second.name => None // with companion
        case definitions if hasProblems(scalaFile, virtualFileName) =>
          val descriptors = definitions.map { clazz =>
            val localQuickFixes = Array[LocalQuickFix](
              new RenameClassQuickFix(clazz, virtualFileName),
              new RenameFileQuickFix(scalaFile, clazz.name + "." + ScalaFileType.INSTANCE.getDefaultExtension)
            )

            manager.createProblemDescriptor(clazz.nameId,
              getDisplayName,
              isOnTheFly,
              localQuickFixes,
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
          }

          Some(descriptors)
        case _ => None
      }

      maybeDescriptors.fold(EMPTY_ARRAY)(_.toArray)
    case _ => EMPTY_ARRAY
  }
}

object ScalaFileNameInspection {

  private def hasProblems(scalaFile: ScalaFile,
                          virtualFileName: String) =
    !scalaFile.typeDefinitions.exists {
      case scalaObject: ScObject if scalaFile.name == "package.scala" && scalaObject.isPackageObject =>
        true
      case clazz: ScTypeDefinition if scalaFile.isScala2File =>
        ScalaNamesUtil.equivalent(clazz.name, virtualFileName)
      case clazz: ScTypeDefinition if scalaFile.isScala3File && scalaFile.members.size == 1 =>
        ScalaNamesUtil.equivalent(clazz.name, virtualFileName)
      case _ =>
        true
    }

  private abstract sealed class RenameQuickFixBase[T <: PsiNamedElement](element: T,
                                                                         name: String,
                                                                         override final val getFamilyName: String)
    extends AbstractFixOnPsiElement(
      ScalaInspectionBundle.message("fileName.rename.text", getFamilyName, element.name, name),
      element
    ) {

    override protected final def doApplyFix(element: T)
                                           (implicit project: Project): Unit = invokeLater {
      onElement(element)
    }

    protected def onElement(element: T)
                           (implicit project: Project): Unit
  }

  private final class RenameClassQuickFix(clazz: ScTypeDefinition, name: String)
    extends RenameQuickFixBase(clazz, name, ScalaInspectionBundle.message("fileName.rename.class")) {

    override protected def onElement(clazz: ScTypeDefinition)
                                    (implicit project: Project): Unit =
      RefactoringFactory.getInstance(project).createRename(clazz, name).run()
  }

  private final class RenameFileQuickFix(file: ScalaFile, name: String)
    extends RenameQuickFixBase(file, name, ScalaInspectionBundle.message("fileName.rename.file")) {

    override protected def onElement(file: ScalaFile)
                                    (implicit project: Project): Unit =
      new RenameProcessor(project, file, name, false, false).run()

    // new RenameRefactoringImpl(project, file, name, false, true)).run
  }

}