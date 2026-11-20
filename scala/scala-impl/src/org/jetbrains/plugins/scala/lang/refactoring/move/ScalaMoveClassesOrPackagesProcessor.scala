package org.jetbrains.plugins.scala.lang.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.MoveDestination
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

import scala.jdk.CollectionConverters._

/**
 * The platform's move of classes and packages, with the conflicts it cannot know about because they come from Scala's
 * rules rather than Java's.
 */
class ScalaMoveClassesOrPackagesProcessor(project: Project,
                                          elements: Array[PsiElement],
                                          destination: MoveDestination,
                                          searchInComments: Boolean,
                                          searchInNonJavaFiles: Boolean,
                                          callback: MoveCallback)
  extends MoveClassesOrPackagesProcessor(project, elements, destination, searchInComments, searchInNonJavaFiles, callback) {

  override protected def showConflicts(conflicts: MultiMap[PsiElement, String], usages: Array[UsageInfo]): Boolean = {
    ScalaMoveClassesOrPackagesProcessor.scalaConflicts(getElements.asScala.toSeq).foreach {
      case (element, message) => conflicts.putValue(element, message)
    }
    super.showConflicts(conflicts, usages)
  }
}

object ScalaMoveClassesOrPackagesProcessor {

  /** The type definitions a move of `elements` takes out of their files: the classes, with their companions if those go too. */
  private def movedTypeDefinitions(elements: Seq[PsiElement]): Seq[ScTypeDefinition] = {
    val classes = elements.collect { case definition: ScTypeDefinition => definition }
    (classes ++ classes.flatMap(companionModule(_, moveCompanion))).distinct
  }

  private[move] def scalaConflicts(elements: Seq[PsiElement]): Seq[(PsiElement, String @Nls)] = {
    val moved = movedTypeDefinitions(elements)
    sealedConflicts(moved)
  }

  /**
   * A subtype of a sealed type must be defined in the same file as that type. Moving either one out of the file alone
   * leaves code that does not compile.
   */
  private def sealedConflicts(moved: Seq[ScTypeDefinition]): Seq[(PsiElement, String @Nls)] = {
    def isSealed(definition: ScTypeDefinition) = definition.getModifierList.isSealed
    def sameFile(a: PsiElement, b: PsiElement) = a.getContainingFile == b.getContainingFile

    val subtypesLeavingTheirParent = for {
      definition <- moved
      parent <- definition.supers.collect { case p: ScTypeDefinition => p }
      if isSealed(parent) && sameFile(parent, definition) && !moved.contains(parent)
    } yield definition -> ScalaBundle.message("move.conflict.extends.sealed", definition.name, parent.name, parent.getContainingFile.getName)

    val sealedTypesLeavingTheirSubtypes = for {
      definition <- moved if isSealed(definition)
      subtype <- definition.getContainingFile.depthFirst().collect { case t: ScTypeDefinition => t }.toSeq
      if !moved.contains(subtype) && subtype.supers.contains(definition)
    } yield definition -> ScalaBundle.message("move.conflict.sealed.subtype.stays", definition.name, subtype.name, definition.getContainingFile.getName)

    subtypesLeavingTheirParent ++ sealedTypesLeavingTheirSubtypes
  }
}
