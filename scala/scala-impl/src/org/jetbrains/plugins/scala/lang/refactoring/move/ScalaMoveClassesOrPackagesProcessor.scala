package org.jetbrains.plugins.scala.lang.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.MoveDestination
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.extensions.IteratorExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import java.lang.ref.Reference
import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Keeps original Scala ASTs alive while the platform moves classes and packages.
 *
 * [[MoveScalaClassHandler]] saves import associations as copyable user data in `prepareMove`
 * and restores them in `finishMoveClass`. This data belongs to the source ASTs, which can be
 * reclaimed even while the PSI elements remain referenced. Collecting a tree between those
 * callbacks can therefore lose imports. Retain the source trees throughout refactoring and
 * fence their reachability on normal and exceptional exits.
 */
final class ScalaMoveClassesOrPackagesProcessor(
  project: Project,
  elements: Array[PsiElement],
  destination: MoveDestination,
  searchInComments: Boolean,
  searchInNonJavaFiles: Boolean,
  callback: MoveCallback
) extends MoveClassesOrPackagesProcessor(project, elements, destination, searchInComments, searchInNonJavaFiles, callback) {

  override protected def performRefactoring(usages: Array[UsageInfo]): Unit = {
    val scalaFiles = getElements.asScala.iterator.map(_.getContainingFile).filterByType[ScalaFile].toSet
    val trees = scalaFiles.toArray.map(_.getNode)
    try {
      super.performRefactoring(usages)
    } finally {
      Reference.reachabilityFence(trees)
    }
  }
}
