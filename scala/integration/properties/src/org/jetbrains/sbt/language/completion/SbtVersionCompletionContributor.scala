package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.lang.Language
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.impl.PropertyValueImpl
import com.intellij.lang.properties.{PropertiesFileType, PropertiesLanguage}
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.{PsiElement, PsiFileFactory}
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.completion.CaptureExt
import org.jetbrains.sbt.language.completion.SbtScalaVersionCompletionContributor.{SbtScalaVersionCompletionProvider, SbtScalaVersionLookupElement}

import scala.collection.mutable

private class SbtVersionCompletionContributor extends SbtScalaVersionCompletionContributor {
  override protected def pattern: ElementPattern[_ <: PsiElement] =
    SbtPsiElementPatterns.propertiesFilePattern && psiElement().inside(SbtPsiElementPatterns.versionPropertyPattern)

  override protected def provider: SbtScalaVersionCompletionProvider = new SbtScalaVersionCompletionProvider {
    override protected def getVersionsByLang(lang: Language): Seq[String] =
      if (lang.getID == PropertiesLanguage.INSTANCE.getID) {
        val versions = mutable.ListBuffer.empty[String]
        collectVersionsFromArtifact("org.scala-sbt", "sbt-launch", versions)
        versions.result()
      } else Seq.empty[String]

    override protected def getVersionLookupElement(version: String): SbtScalaVersionLookupElement =
      new SbtScalaVersionLookupElement(version) {
        override def handleInsert(context: InsertionContext): Unit = {
          val caretModel = context.getEditor.getCaretModel
          val psiElement = context.getFile.findElementAt(caretModel.getVisualLineStart).getParent.getLastChild
          inWriteAction {
            psiElement match {
              case value: PropertyValueImpl =>
                val newPropertiesFile = PsiFileFactory.getInstance(context.getProject)
                  .createFileFromText(
                    s"dummy.${PropertiesFileType.INSTANCE.getDefaultExtension}",
                    PropertiesLanguage.INSTANCE,
                    s"sbt.version = $version",
                    false, true
                  )
                  .asInstanceOf[PropertiesFile]
                value.replace(newPropertiesFile.getContainingFile.getFirstChild.getLastChild.getLastChild)
              case _ =>
            }
          }
        }
      }
  }
}
