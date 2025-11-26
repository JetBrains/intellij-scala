package org.jetbrains.plugins.scala.bazel

import com.intellij.psi.PsiElement
import org.jetbrains.bazel.java.ui.gutters.BazelJavaRunLineMarkerContributor

import java.util

/**
 *  - Inherits logic for running scala from BazelJavaRunLineMarkerContributor
 *  - Adds logic for running entire test classes and individual tests via Bazel (see [[BazelScalaTestRunLineMarkerLogic]]
 */
class BazelScalaRunLineMarkerContributor extends BazelJavaRunLineMarkerContributor {

  override def shouldAddMarker(psiElement: PsiElement): Boolean =
    super.shouldAddMarker(psiElement) || BazelScalaTestRunLineMarkerLogic.shouldAddMarker(psiElement)

  override def getSingleTestFilter(psiElement: PsiElement): String =
    BazelScalaTestRunLineMarkerLogic.getSingleTestFilter(psiElement)

  override def getExtraProgramArguments(psiElement: PsiElement): util.List[String] =
    BazelScalaTestRunLineMarkerLogic.getExtraProgramArguments(psiElement)
}
