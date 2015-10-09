package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.project.Project
import com.intellij.psi.search.{GlobalSearchScopesCore, PsiSearchHelper}
import com.intellij.psi.{PsiDirectory, PsiElement, PsiFile, PsiNamedElement}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.ScalaBundle

import scala.collection.mutable.ArrayBuffer

/**
 * Created by Kate Ustyuzhanina on 8/25/15.
 */
object ScalaCompositeTypeValidator {
  def apply(validators: List[ScalaValidator],
            conflictsReporter: ConflictsReporter,
            myProject: Project,
            selectedElement: PsiElement,
            noOccurrences: Boolean,
            enclosingContainerAll: PsiElement,
            enclosingOne: PsiElement): ScalaCompositeTypeValidator = {
    new ScalaCompositeTypeValidator(conflictsReporter, myProject,
      selectedElement, noOccurrences, enclosingContainerAll, enclosingOne, validators)
  }
}


class ScalaCompositeTypeValidator(conflictsReporter: ConflictsReporter,
                                  myProject: Project,
                                  selectedElement: PsiElement,
                                  noOccurrences: Boolean,
                                  enclosingContainerAll: PsiElement,
                                  enclosingOne: PsiElement,
                                  validators: List[ScalaValidator])
  extends ScalaTypeValidator(conflictsReporter, myProject, selectedElement, noOccurrences, enclosingContainerAll, enclosingOne) {

  override def findConflicts(name: String, allOcc: Boolean): Array[(PsiNamedElement, String)] = {
    //returns declaration and message
    val buf = new ArrayBuffer[(PsiNamedElement, String)]


    val filesToSearchIn = enclosingContainerAll match {
      case directory: PsiDirectory =>
        findFilesForDownConflictFindings(directory, name)
      case _ => null
    }


    for (file <- filesToSearchIn) {
      if (buf.isEmpty) {
        buf ++= getForbiddenNamesInBlock(file, name)
      }
    }

    for (validator <- validators) {
      if (buf.isEmpty) {
        buf ++= getForbiddenNames(validator.enclosingContainer(allOcc), name)
      }
    }

    buf.toArray
  }

  //TODO iliminate duplication
  private def findFilesForDownConflictFindings(directory: PsiDirectory, name: String): Array[PsiFile] = {
    def oneRound(word: String) = {
      val buffer = new ArrayBuffer[PsiFile]()

      val processor = new Processor[PsiFile] {
        override def process(file: PsiFile): Boolean = {
          buffer += file
          true
        }
      }

      val helper: PsiSearchHelper = PsiSearchHelper.SERVICE.getInstance(directory.getProject)
      helper.processAllFilesWithWord(word, GlobalSearchScopesCore.directoryScope(directory, true), processor, true)

      buffer
    }

    val resultBuffer = oneRound(name)
    resultBuffer.toArray
  }

  override def validateName(name: String, increaseNumber: Boolean): String = {
    val newName = name.capitalize
    super.validateName(newName, increaseNumber)
  }

  private def messageForTypeAliasMember(name: String) =
    ScalaBundle.message("introduced.typealias.will.conflict.with.type.name", name)

  private def messageForClassMember(name: String) =
    ScalaBundle.message("introduced.typealias.will.conflict.with.class.name", name)
}

