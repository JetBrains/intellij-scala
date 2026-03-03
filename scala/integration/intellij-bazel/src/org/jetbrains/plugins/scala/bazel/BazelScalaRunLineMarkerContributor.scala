package org.jetbrains.plugins.scala.bazel

import com.intellij.psi.PsiElement
import org.jetbrains.bazel.java.ui.gutters.BazelJavaRunLineMarkerContributor
import org.jetbrains.plugins.scala.incremental.Highlighting.ElementHighlightingExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 *  - Inherits logic for running scala from BazelJavaRunLineMarkerContributor
 *  - Adds logic for running entire test classes and individual tests via Bazel (see [[BazelScalaTestRunLineMarkerLogic]]
 */
class BazelScalaRunLineMarkerContributor extends BazelJavaRunLineMarkerContributor {

  override def shouldAddMarker(psiElement: PsiElement): Boolean = {
    val file = psiElement.getContainingFile
    val project = if (file != null) file.getProject else psiElement.getProject // Avoid tree walk-up

    if (!ScalaProjectSettings.in(project).isDisableInspections) {
      if (!psiElement.isVisible(project, file)) return false
    }

    super.shouldAddMarker(psiElement) || BazelScalaTestRunLineMarkerLogic.shouldAddMarker(psiElement)
  }

  override def getSingleTestFilter(psiElement: PsiElement): String =
    BazelScalaTestRunLineMarkerLogic.getSingleTestFilter(psiElement)

  override def getExtraProgramArguments(psiElement: PsiElement): util.List[String] =
    BazelScalaTestRunLineMarkerLogic.getExtraProgramArguments(psiElement).asJava
}
