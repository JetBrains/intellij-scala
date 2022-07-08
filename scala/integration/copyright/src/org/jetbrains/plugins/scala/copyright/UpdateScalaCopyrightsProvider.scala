package org.jetbrains.plugins.scala
package copyright

import com.intellij.copyright.UpdateJavaFileCopyright
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.maddyhome.idea.copyright._
import org.jetbrains.plugins.scala.lang.psi.api._

import scala.annotation.tailrec

final class UpdateScalaCopyrightsProvider extends psi.UpdateCopyrightsProvider {

  override def createInstance(project: Project,
                              module: Module,
                              file: VirtualFile,
                              base: FileType,
                              options: CopyrightProfile): UpdateJavaFileCopyright =
    new UpdateJavaFileCopyright(project, module, file, options) {
      override def accept: Boolean = getFile.isInstanceOf[ScalaFile]

      override def getPackageStatement: PsiElement = getFile match {
        case file: ScalaFile =>
          if (file.isScriptFile) file.getFirstChild
          else file.firstPackaging.orNull
        case _ => null
      }

      override def getImportsList: Array[PsiElement] = getFile match {
        case file: ScalaFile =>
          UpdateScalaCopyrightsProvider.importStatementsInHeader(file) match {
            case Nil => null
            case seq => seq.toArray
          }
        case _ => null
      }
    }
}

object UpdateScalaCopyrightsProvider {

  import toplevel._
  import imports.ScImportStmt

  private def importStatementsInHeader(element: PsiElement): List[ScImportStmt] = {
    @tailrec
    def importStatementsInHeader(children: List[PsiElement], result: List[ScImportStmt]): List[ScImportStmt] = children.head match {
      case _: typedef.ScTypeDefinition => result
      case packaging: ScPackaging =>
        if (result.nonEmpty || packaging.isExplicit) result
        else this.importStatementsInHeader(packaging)
      case child =>
        val newResult = child match {
          case statement: ScImportStmt => statement :: result
          case _ => result
        }
        importStatementsInHeader(children.tail, newResult)
    }

    importStatementsInHeader(element.getChildren.toList, Nil)
  }
}