package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.Language
import com.intellij.lang.properties.PropertiesLanguage
import com.intellij.lang.properties.psi.impl.PropertyValueImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.plugins.scala.lang.completion.CaptureExt
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.sbt.language.completion.SbtScalaVersionCompletionContributor.SbtScalaVersionCompletionProvider
import org.jetbrains.sbt.language.completion.SbtVersionCompletionContributor.{SbtGroupId, SbtLaunchArtifactId}

private class SbtVersionCompletionContributor extends SbtScalaVersionCompletionContributor with DumbAware {
  override protected def pattern: ElementPattern[_ <: PsiElement] =
    SbtPsiElementPatterns.propertiesFilePattern && psiElement().inside(SbtPsiElementPatterns.versionPropertyPattern)

  override protected def provider: SbtScalaVersionCompletionProvider = new SbtScalaVersionCompletionProvider {
    override protected def getVersionsByLang(lang: Language, onlyStableVersions: Boolean): Seq[ComparableVersion] =
      if (lang.getID == PropertiesLanguage.INSTANCE.getID) {
        DependencyUtil.getArtifactVersions(SbtGroupId, SbtLaunchArtifactId, onlyStableVersions)
      } else Seq.empty

    override protected def getVersionLookupElement(version: ComparableVersion): LookupElementBuilder =
      super.getVersionLookupElement(version)
        .withInsertHandler { (context, _) =>
          context.commitDocument()
          val caretModel = context.getEditor.getCaretModel
          context.getFile.findElementAt(caretModel.getOffset - 1) match {
            case value: PropertyValueImpl =>
              val document = context.getDocument
              document.replaceString(value.getTextRange.getStartOffset, value.getTextRange.getEndOffset, version.toString)
              PsiDocumentManager.getInstance(context.getProject).doPostponedOperationsAndUnblockDocument(document)
            case _ =>
          }
        }
  }
}

object SbtVersionCompletionContributor {
  private[completion] val SbtGroupId = "org.scala-sbt"
  private[completion] val SbtLaunchArtifactId = "sbt"
}
