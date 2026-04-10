package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.{FileModifier, IntentionAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder

final class ImportNamedTypeArgumentsFeatureFlagQuickFix(element: PsiElement)
  extends IntentionAction {

  override def getText: String = ImportNamedTypeArgumentsFeatureFlagQuickFix.Text

  override def getFamilyName: String = getText

  override def invoke(project: Project, editor: Editor, psiFile: PsiFile): Unit = {
    val importsHolder = element.getContainingFile match {
      case holder: ScImportsHolder => holder
      case _                       => ScImportsHolder.forNewImportInsertion(element)
    }

    importsHolder.addImportForPath(ImportNamedTypeArgumentsFeatureFlagQuickFix.FeatureImportPath, element)
  }

  override def startInWriteAction(): Boolean = true

  override def isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean =
    element != null && element.isValid

  override def getFileModifierForPreview(target: PsiFile): FileModifier =
    new ImportNamedTypeArgumentsFeatureFlagQuickFix(PsiTreeUtil.findSameElementInCopy(element, target))
}

private object ImportNamedTypeArgumentsFeatureFlagQuickFix {
  private val FeatureName = ScalaInspectionBundle.message("language.feature.named.type.argument")
  private[quickfix] val FeatureImportPath = "scala.language.experimental.namedTypeArguments"

  private[quickfix] val Text = ScalaInspectionBundle.message("import.feature.flag.for.language.feature", FeatureName)
}
