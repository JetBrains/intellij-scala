package org.jetbrains.plugins.scala.bazel

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.bazel.ui.gutters.BazelRunConfigurationProducer
import org.jetbrains.bazel.ui.gutters.BazelRunConfigurationProducer.GutterAction
import org.jetbrains.bsp.protocol.BuildTarget
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.incremental.Highlighting.ElementHighlightingExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.ScalaMainMethodUtil

import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 *  - Inherits logic for running scala from BazelJavaRunLineMarkerContributor
 *  - Adds logic for running entire test classes and individual tests via Bazel (see [[BazelScalaTestRunLineMarkerLogic]]
 */
//noinspection ApiStatus,UnstableApiUsage
class BazelScalaRunConfigurationProducer extends BazelRunConfigurationProducer {

  @Nullable
  override def getGutterAction(psiElement: PsiElement, target: BuildTarget): GutterAction = {
    val file = psiElement.getContainingFile
    if (file == null) return null
    if (!file.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) return null

    val project = file.getProject
    if (!ScalaProjectSettings.in(project).isDisableInspections) {
      if (!psiElement.isVisible(project, file)) return null
    }

    if (ScalaMainMethodUtil.hasMain(psiElement)) {
      // main() methods don't need a test filter or additional arguments
      return GutterAction()
    }

    if (!BazelScalaTestRunLineMarkerLogic.shouldAddMarker(psiElement)) null
    else {
      val testFilter = BazelScalaTestRunLineMarkerLogic.getSingleTestFilter(psiElement)
      if (testFilter == null) return null

      val arguments = BazelScalaTestRunLineMarkerLogic.getExtraProgramArguments(psiElement).asJava
      GutterAction(testFilter, arguments, java.util.Collections.emptyMap(), null)
    }
  }
}
