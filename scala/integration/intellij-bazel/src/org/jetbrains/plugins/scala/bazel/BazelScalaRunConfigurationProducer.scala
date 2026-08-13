package org.jetbrains.plugins.scala.bazel

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.bazel.java.ui.gutters.BazelJavaRunConfigurationProducer
import org.jetbrains.bazel.ui.gutters.BazelRunConfigurationProducer.GutterAction
import org.jetbrains.bsp.protocol.BuildTarget
import org.jetbrains.plugins.scala.incremental.Highlighting.ElementHighlightingExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 *  - Inherits logic for running scala from BazelJavaRunLineMarkerContributor
 *  - Adds logic for running entire test classes and individual tests via Bazel (see [[BazelScalaTestRunLineMarkerLogic]]
 */
//noinspection ApiStatus,UnstableApiUsage
// TODO(BAZEL-3360): use BazelRunLineMarkerContributor
//   BazelJavaRunConfigurationProducer's comment says: "External plugins (e.g., Scala) should implement BazelRunConfigurationProducer instead"
class BazelScalaRunConfigurationProducer extends BazelJavaRunConfigurationProducer {

  @Nullable
  override def getGutterAction(psiElement: PsiElement, target: BuildTarget): GutterAction = {
    val file = psiElement.getContainingFile
    val project = if (file != null) file.getProject else psiElement.getProject // Avoid tree walk-up

    if (!ScalaProjectSettings.in(project).isDisableInspections) {
      if (!psiElement.isVisible(project, file)) return null
    }

    val action = super.getGutterAction(psiElement, target)
    if (action != null) return action

    if (!BazelScalaTestRunLineMarkerLogic.shouldAddMarker(psiElement)) null
    else {
      val testFilter = BazelScalaTestRunLineMarkerLogic.getSingleTestFilter(psiElement)
      if (testFilter == null) return null

      val arguments = BazelScalaTestRunLineMarkerLogic.getExtraProgramArguments(psiElement).asJava
      GutterAction(testFilter, arguments, java.util.Collections.emptyMap(), null)
    }
  }
}
