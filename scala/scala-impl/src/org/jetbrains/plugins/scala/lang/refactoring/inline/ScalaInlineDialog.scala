package org.jetbrains.plugins.scala.lang.refactoring.inline

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiReference
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.inline.InlineOptionsDialog
import com.intellij.refactoring.inline.InlineOptionsDialog.initOccurrencesNumber
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, OptionExt, ToNullSafe}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import java.util.concurrent.Callable

abstract class ScalaInlineDialog(element: ScNamedElement, @NlsContexts.DialogTitle title: String, helpId: String)
                                (implicit project: Project, editor: Editor) extends InlineOptionsDialog(project, true, element) {
  protected val occurrencesNumber: Int =
    ScalaInlineDialog.computeInNonBlockingReadActionUnderProgress(ScalaBundle.message("inline.occurrences.calculate.progress.title")) {
      initOccurrencesNumber(element)
    }

  protected final val referenceAtCaret: Option[PsiReference] =
    TargetElementUtil
      .findReference(editor, editor.getCaretModel.getOffset)
      .toOption

  // Needs to be lazy as it is referenced in the init block. Otherwise, it will be null if overridden
  protected lazy val reference: Option[PsiReference] =
    referenceAtCaret.filterByType[ScReferenceExpression]

  locally {
    // must be set before `init()` call
    myInvokedOnReference = reference.isDefined && occurrencesNumber != 1

    init()
    setTitle(title)
  }

  protected final def settings: ScalaApplicationSettings = ScalaApplicationSettings.getInstance()

  protected def inlineThisGetter: ScalaApplicationSettings => Boolean
  protected def inlineThisSetter: (ScalaApplicationSettings, Boolean) => Unit
  override def isInlineThis: Boolean = inlineThisGetter(settings)

  protected def inlineKeepGetter: ScalaApplicationSettings => Boolean
  protected def inlineKeepSetter: (ScalaApplicationSettings, Boolean) => Unit
  override def isKeepTheDeclarationByDefault: Boolean = inlineKeepGetter(settings)

  protected def createProcessor(): BaseRefactoringProcessor

  @Nls protected def getInlineAllUsagesAndRemoveDefinitionText: String
  final override def getInlineAllText: String =
    if (element.isWritable) getInlineAllUsagesAndRemoveDefinitionText
    else ScalaBundle.message("inline.all.usages.in.project")

  override val getHelpId: String = helpId

  override def doAction(): Unit = {
    invokeRefactoring(createProcessor())
    updateSettings()
  }

  private def updateSettings(): Unit = {
    if (myRbInlineThisOnly.isEnabled && myRbInlineAll.isEnabled) {
      inlineThisSetter(settings, isInlineThisOnly)
    }
    if (myKeepTheDeclaration.nullSafe.exists(_.isEnabled)) {
      inlineKeepSetter(settings, isKeepTheDeclaration)
    }
  }
}

object ScalaInlineDialog {
  /**
   * A copy of the private `com.intellij.codeInsight.navigation.GotoTargetHandler.computeInNonBlockingReadActionUnderProgress`,
   * the platform's replacement for the deprecated `ActionUtil.underModalProgress` (IJPL-260316).
   */
  private def computeInNonBlockingReadActionUnderProgress[T](@NlsContexts.ProgressTitle title: String)
                                                            (body: => T)
                                                            (implicit project: Project): T = {
    val dumbService = DumbService.getInstance(project)
    val alternativeResolve = dumbService.isAlternativeResolveEnabled
    val computation: Callable[T] = () =>
      if (alternativeResolve) dumbService.computeWithAlternativeResolveEnabled[T, RuntimeException](() => body)
      else body

    ProgressManager.getInstance.runProcessWithProgressSynchronously[T, RuntimeException](
      () => ReadAction.nonBlocking(computation).executeSynchronously(),
      title,
      /*canBeCanceled*/ true,
      project
    )
  }
}
