package org.jetbrains.plugins.scala
package lang
package refactoring
package move

import java.{util => ju}

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiDirectory, PsiDocumentManager, PsiElement, PsiFile}
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassHandler
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

import scala.jdk.CollectionConverters._

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

  override def doMoveClass(clazz: PsiClass, directory: PsiDirectory): PsiClass = (clazz, clazz.getContainingFile) match {
    case (definition: ScTypeDefinition, file: ScalaFile) =>
      val maybeCompanion = if (moveCompanion) definition.baseCompanion
      else None

      val createNewClass = directory.findFile(file.getName) match {
        case fileWithOldFileName: PsiFile if directory != file.getContainingDirectory && classCanBeAdded(file, clazz) =>
          // moving second of two classes which were in the same file to a different directory (IDEADEV-3089)
          fileWithOldFileName.add _
        case _ =>
          //moving class to the existing file with the same name
          directory.findFile(fileName(definition)) match {
            case fileWithClassName: PsiFile if classCanBeAdded(fileWithClassName, clazz) =>
              fileWithClassName.add _
            case _ =>
              //create new file with template
              import actions.ScalaFileTemplateUtil._
              val template: String = definition match {
                case _: ScClass => SCALA_CLASS
                case _: ScTrait => SCALA_TRAIT
                case _: ScObject => SCALA_OBJECT
              }

              val created = util.ScalaDirectoryService.createClassFromTemplate(directory, definition.name, template, askToDefineVariables = false)
              if (definition.getDocComment == null) {
                created.getDocComment match {
                  case null =>
                  case createdComment =>
                    definition.addAfter(createdComment, null)
                    Associations.shiftFor(definition, createdComment.getTextLength)
                }
              }

              created.replace _
          }
      }

      val (newClass, fileWasDeleted) = deleteClass(definition)(createNewClass)
      if (fileWasDeleted) newClass.navigate(true)

      maybeCompanion.foreach { companion =>
        deleteClass(companion)(newClass.getContainingFile.add)
      }
      newClass
    case _ => null
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

  private def deleteClass(clazz: ScTypeDefinition)
                         (createNewClass: PsiClass => PsiElement): (PsiClass, Boolean) = {
    val newClass = createNewClass(clazz).asInstanceOf[PsiClass]

    val file = clazz.getContainingFile.asInstanceOf[ScalaFile]
    val elementToDelete = file.typeDefinitions match {
      case Seq(`clazz`) if !(file.isScriptFile || file.isWorksheetFile) => file
      case _ => clazz
    }
    elementToDelete.delete()

    (newClass, elementToDelete == file)
  }

  private def classCanBeAdded(file: PsiFile, clazz: PsiClass): Boolean = {
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
