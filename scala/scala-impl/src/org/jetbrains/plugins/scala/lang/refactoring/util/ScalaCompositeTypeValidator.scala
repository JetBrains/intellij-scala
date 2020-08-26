package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.project.Project
import com.intellij.psi.search.{GlobalSearchScopesCore, PsiSearchHelper}
import com.intellij.psi.{PsiDirectory, PsiElement, PsiFile, PsiNamedElement}
import com.intellij.util.Processor

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
  extends ScalaTypeValidator(selectedElement, noOccurrences, enclosingContainerAll, enclosingOne) {

  protected override def findConflictsImpl(name: String, allOcc: Boolean): collection.Seq[(PsiNamedElement, String)] = {
    //returns declaration and message
    val buf = new ArrayBuffer[(PsiNamedElement, String)]


    val filesToSearchIn = enclosingContainerAll match {
      case directory: PsiDirectory =>
        findFilesForDownConflictFindings(directory, name)
      case _ => Seq.empty
    }

    for (file <- filesToSearchIn) {
      if (buf.isEmpty) {
        buf ++= forbiddenNamesInBlock(file, name)
      }
    }

    for (validator <- validators) {
      if (buf.isEmpty) {
        buf ++= forbiddenNames(validator.enclosingContainer(allOcc), name)
      }
    }

    buf
  }

  //TODO eliminate duplication
  private def findFilesForDownConflictFindings(directory: PsiDirectory, name: String): collection.Seq[PsiFile] = {
    val buffer = new ArrayBuffer[PsiFile]()

    val processor = new Processor[PsiFile] {
      override def process(file: PsiFile): Boolean = {
        buffer += file
        true
      }
    }

    val helper: PsiSearchHelper = PsiSearchHelper.getInstance(directory.getProject)
    helper.processAllFilesWithWord(name, GlobalSearchScopesCore.directoryScope(directory, true), processor, true)

    buffer
  }
}

