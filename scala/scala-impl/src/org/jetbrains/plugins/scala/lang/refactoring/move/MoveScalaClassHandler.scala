package org.jetbrains.plugins.scala
package lang
package refactoring
package move

import java.{util => ju}

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiDirectory, PsiDocumentManager, PsiFile}
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassHandler
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

import scala.collection.JavaConverters

final class MoveScalaClassHandler extends MoveClassHandler {

  import MoveScalaClassHandler._

  override def prepareMove(clazz: PsiClass): Unit = clazz.getContainingFile match {
    case scalaFile: ScalaFile =>
      Stats.trigger(FeatureKey.moveClass)
      collectAssociations(clazz, scalaFile, moveCompanion)
    case _ =>
  }

  override def finishMoveClass(clazz: PsiClass): Unit = clazz.getContainingFile match {
    case scalaFile: ScalaFile =>
      restoreAssociations(clazz)
      val documentManager = PsiDocumentManager.getInstance(scalaFile.getProject)
      documentManager.getDocument(scalaFile) match {
        case null =>
        case document =>
          documentManager.doPostponedOperationsAndUnblockDocument(document)
          new ScalaImportOptimizer().processFile(scalaFile).run()
      }
    case _ =>
  }

  override def doMoveClass(clazz: PsiClass, directory: PsiDirectory): PsiClass = {
    var fileWasDeleted: Boolean = false
    val maybeCompanion = companionModule(clazz, moveCompanion)

    def deleteClass(aClass: PsiClass): Unit = aClass.getContainingFile match {
      case file: ScalaFile =>
        file.typeDefinitions match {
          case Seq(`aClass`) if !(file.isScriptFile || file.isWorksheetFile) =>
            file.delete()
            fileWasDeleted = true
          case _ => aClass.delete()
        }
    }

    var newClass: PsiClass = null

    (clazz, clazz.getContainingFile) match {
      case (td: ScTypeDefinition, file: ScalaFile) =>
        val fileWithOldFileName = directory.findFile(file.getName)
        val fileWithClassName = directory.findFile(fileName(td))

        // moving second of two classes which were in the same file to a different directory (IDEADEV-3089)
        if (directory != file.getContainingDirectory && fileWithOldFileName != null && classCanBeAdded(file, clazz)) {
          newClass = fileWithOldFileName.add(td).asInstanceOf[PsiClass]
          deleteClass(td)
        }
        //moving class to the existing file with the same name
        else if (fileWithClassName != null && classCanBeAdded(fileWithClassName, clazz)) {
          newClass = fileWithClassName.add(td).asInstanceOf[PsiClass]
          deleteClass(td)
        }
        //create new file with template
        else {
          import actions.ScalaFileTemplateUtil._
          val template: String = td match {
            case _: ScClass => SCALA_CLASS
            case _: ScTrait => SCALA_TRAIT
            case _: ScObject => SCALA_OBJECT
          }

          val created = util.ScalaDirectoryService.createClassFromTemplate(directory, td.name, template, askToDefineVariables = false)
          if (td.getDocComment == null) {
            created.getDocComment match {
              case null =>
              case createdComment =>
                td.addAfter(createdComment, null)
                shiftAssociations(td, createdComment.getTextLength)
            }
          }
          newClass = created.replace(td).asInstanceOf[PsiClass]
          deleteClass(td)
        }
      case _ =>
    }

    if (fileWasDeleted) newClass.navigate(true)
    maybeCompanion.foreach { companion =>
      newClass.getContainingFile.add(companion)
      deleteClass(companion)
    }

    newClass
  }

  override def getName(clazz: PsiClass): String = clazz.getContainingFile match {
    case scalaFile: ScalaFile =>
      if (scalaFile.typeDefinitions.length > 1) fileName(clazz.asInstanceOf[ScTemplateDefinition])
      else scalaFile.getName
    case _ => null
  }

  override def preprocessUsages(collection: ju.Collection[UsageInfo]): Unit = {}
}

object MoveScalaClassHandler {

  private def fileName(templateDefinition: ScTemplateDefinition) =
    templateDefinition.name + "." + ScalaFileType.INSTANCE.getDefaultExtension

  private def classCanBeAdded(file: PsiFile, clazz: PsiClass): Boolean = {
    import JavaConverters._

    val allClasses = PsiTreeUtil.findChildrenOfType(file, classOf[ScTypeDefinition])
    val className = util.ScalaNamesUtil.scalaName(clazz)
    val withSameName = allClasses.asScala.filter {
      _.name == className
    }.toList

    withSameName match {
      case Nil => true
      case head :: Nil =>
        clazz match {
          case typeDefinition: ScTypeDefinition => canBeCompanions(head, typeDefinition)
          case _ => false
        }
      case _ => false
    }
  }

  private[this] def canBeCompanions(left: ScTypeDefinition, right: ScTypeDefinition): Boolean = left.name == right.name && {
    val classes = Seq(left, right)
    classes.count(_.isInstanceOf[ScObject]) == 1 &&
      !classes.exists(_.fakeCompanionModule.isDefined)
  }

}
