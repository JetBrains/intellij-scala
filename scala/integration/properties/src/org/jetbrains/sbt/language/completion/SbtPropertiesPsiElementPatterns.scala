package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.lang.properties.psi.Property
import com.intellij.patterns.PlatformPatterns.{psiElement, psiFile}
import com.intellij.patterns.PsiElementPattern.Capture
import com.intellij.patterns.StandardPatterns.instanceOf
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.condition

object SbtPropertiesPsiElementPatterns:
  def propertiesFilePattern: Capture[PsiElement] = psiElement.inFile:
    psiFile.withFileType(instanceOf(classOf[PropertiesFileType]))

  def versionPropertyPattern: Capture[PsiElement] = psiElement().`with`(
    condition[PsiElement]("isVersionPropertyPattern"):
      case property: Property =>
        property.getKey == "sbt.version" && property.getValue.contains(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED)
      case _ => false
  )
