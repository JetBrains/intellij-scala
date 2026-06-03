package org.jetbrains.plugins.scala.lang.refactoring.inline.method

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.refactoring.BaseRefactoringProcessor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineDialog
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

final class ScalaInlineMethodDialog(method: ScFunctionDefinition)(implicit project: Project, editor: Editor)
  extends ScalaInlineDialog(method, title = ScalaInlineMethodHandler.RefactoringName, helpId = ScalaInlineMethodHandler.HelpId) {
  override protected def createProcessor(): BaseRefactoringProcessor =
    new ScalaInlineMethodProcessor(
      method,
      reference,
      inlineThisOnly = isInlineThisOnly,
      removeDefinition = method.isWritable && !isKeepTheDeclaration,
    )

  override protected def inlineThisGetter: ScalaApplicationSettings => Boolean = _.INLINE_METHOD_THIS
  override protected def inlineThisSetter: (ScalaApplicationSettings, Boolean) => Unit = _.INLINE_METHOD_THIS = _

  override protected def inlineKeepGetter: ScalaApplicationSettings => Boolean = _.INLINE_METHOD_KEEP
  override protected def inlineKeepSetter: (ScalaApplicationSettings, Boolean) => Unit = _.INLINE_METHOD_KEEP = _

  override def getNameLabelText: String =
    if (method.isLocal) ScalaBundle.message("inline.local.method.name.label", method.name)
    else ScalaBundle.message("inline.method.name.label", method.name)

  override def getInlineAllUsagesAndRemoveDefinitionText: String =
    ScalaBundle.message("inline.all.usages.and.remove.the.method")

  override def getInlineThisText: String = ScalaBundle.message("inline.this.usage.only.and.keep.the.method")

  override def getKeepTheDeclarationText: String =
    if (method.isWritable) ScalaBundle.message("inline.all.usages.and.keep.the.method")
    else null
}
