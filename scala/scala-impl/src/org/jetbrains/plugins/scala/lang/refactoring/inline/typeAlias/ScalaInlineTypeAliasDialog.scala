package org.jetbrains.plugins.scala.lang.refactoring.inline.typeAlias

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiReference
import com.intellij.refactoring.BaseRefactoringProcessor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineDialog
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

final class ScalaInlineTypeAliasDialog(typeAlias: ScTypeAliasDefinition)(implicit project: Project, editor: Editor)
  extends ScalaInlineDialog(typeAlias, title = ScalaInlineTypeAliasHandler.RefactoringName, helpId = ScalaInlineTypeAliasHandler.HelpId) {
  override protected def createProcessor(): BaseRefactoringProcessor =
    new ScalaInlineTypeAliasProcessor(
      typeAlias,
      reference,
      inlineThisOnly = isInlineThisOnly,
      removeDefinition = typeAlias.isWritable && !isKeepTheDeclaration,
    )

  override protected def inlineThisGetter: ScalaApplicationSettings => Boolean = _.INLINE_TYPE_ALIAS_THIS
  override protected def inlineThisSetter: (ScalaApplicationSettings, Boolean) => Unit = _.INLINE_TYPE_ALIAS_THIS = _

  override protected def inlineKeepGetter: ScalaApplicationSettings => Boolean = _.INLINE_TYPE_ALIAS_KEEP
  override protected def inlineKeepSetter: (ScalaApplicationSettings, Boolean) => Unit = _.INLINE_TYPE_ALIAS_KEEP = _

  override def getNameLabelText: String =
    if (typeAlias.isLocal) ScalaBundle.message("inline.local.type.alias.name.label", typeAlias.name)
    else ScalaBundle.message("inline.type.alias.name.label", typeAlias.name)

  override def getInlineAllUsagesAndRemoveDefinitionText: String =
    ScalaBundle.message("inline.all.usages.and.remove.the.type.alias")

  override def getInlineThisText: String = ScalaBundle.message("inline.this.usage.only.and.keep.the.type.alias")

  override def getKeepTheDeclarationText: String =
    if (typeAlias.isWritable) ScalaBundle.message("inline.all.usages.and.keep.the.type.alias")
    else null

  override protected lazy val reference: Option[PsiReference] =
    referenceAtCaret.filterByType[ScStableCodeReference]
}
