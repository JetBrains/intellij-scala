package org.jetbrains.plugins.scala
package lang.refactoring.move

import com.intellij.openapi.util.Key
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiDirectory, PsiElement, PsiFile}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.actions.ScalaFileTemplateUtil
import org.jetbrains.plugins.scala.conversion.copy.{Associations, ScalaCopyPastePostProcessor}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getBaseCompanionModule
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaFile}
import org.jetbrains.plugins.scala.lang.refactoring.util.{ScalaDirectoryService, ScalaNamesUtil}

import scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 10/24/13
 */
object ScalaMoveUtil {
  val MOVE_DESTINATION: Key[PsiDirectory] = Key.create[PsiDirectory]("MoveDestination")
  val MOVE_SOURCE: Key[PsiFile] = Key.create("MoveSource")
  private val PROCESSOR: ScalaCopyPastePostProcessor = new ScalaCopyPastePostProcessor
  private val ASSOCIATIONS_KEY: Key[Associations] = Key.create("ASSOCIATIONS")


  def canBeCompanions(class1: PsiClass, class2: PsiClass): Boolean = {
    (class1, class2) match {
      case (td1: ScTypeDefinition, td2: ScTypeDefinition) if td1.name == td2.name =>
        val classes = Seq(td1, td2)
        val noFakeCompanions = classes.collect { case td: ScTypeDefinition => td.fakeCompanionModule.isDefined }.isEmpty
        classes.count(_.isInstanceOf[ScObject]) == 1 && noFakeCompanions
      case _ => false
    }
  }

  def classCanBeAdded(file: PsiFile, aClass: PsiClass): Boolean = {
    val allClasses = PsiTreeUtil.findChildrenOfType(file, classOf[ScTypeDefinition])
    val withSameName = allClasses.asScala.filter(_.name == ScalaNamesUtil.scalaName(aClass))
    withSameName.size == 1 && canBeCompanions(withSameName.head, aClass) || withSameName.isEmpty
  }

  def doMoveClass(@NotNull aClass: PsiClass, @NotNull moveDestination: PsiDirectory, withCompanion: Boolean): PsiClass = {
    var fileWasDeleted: Boolean = false
    val maybeCompanion = companionModule(aClass, withCompanion)

    def deleteClass(aClass: PsiClass): Unit = aClass.getContainingFile match {
      case file: ScalaFile =>
        file.typeDefinitions match {
          case Seq(`aClass`) if !file.isScriptFile() && !file.isWorksheetFile =>
            file.delete()
            fileWasDeleted = true
          case _ => aClass.delete()
        }
    }

    def moveClassInner(aClass: PsiClass, moveDestination: PsiDirectory): PsiClass = {
      var newClass: PsiClass = null

      (aClass, aClass.getContainingFile) match {
        case (td: ScTypeDefinition, file: ScalaFile) =>
          val fileWithOldFileName = moveDestination.findFile(file.getName)
          val className = td.name
          val fileWithClassName = moveDestination.findFile(s"$className.${ScalaFileType.INSTANCE.getDefaultExtension}")
          // moving second of two classes which were in the same file to a different directory (IDEADEV-3089)
          if (moveDestination != file.getContainingDirectory && fileWithOldFileName != null && classCanBeAdded(file, aClass)) {
            newClass = fileWithOldFileName.add(td).asInstanceOf[PsiClass]
            deleteClass(td)
          }
          //moving class to the existing file with the same name
          else if (fileWithClassName != null && classCanBeAdded(fileWithClassName, aClass)) {
            newClass = fileWithClassName.add(td).asInstanceOf[PsiClass]
            deleteClass(td)
          }
          //create new file with template
          else {
            val template: String = td match {
              case _: ScClass => ScalaFileTemplateUtil.SCALA_CLASS
              case _: ScTrait => ScalaFileTemplateUtil.SCALA_TRAIT
              case _: ScObject => ScalaFileTemplateUtil.SCALA_OBJECT
            }
            val created: PsiClass = ScalaDirectoryService.createClassFromTemplate(moveDestination, td.name, template, askToDefineVariables = false)
            if (td.getDocComment == null) {
              val createdDocComment: PsiDocComment = created.getDocComment
              if (createdDocComment != null) {
                td.addAfter(createdDocComment, null)
                shiftAssociations(td, createdDocComment.getTextLength)
              }
            }
            newClass = created.replace(td).asInstanceOf[PsiClass]
            deleteClass(td)
          }

        case _ =>
      }
      if (fileWasDeleted) newClass.navigate(true)
      newClass
    }

    val movedClass = moveClassInner(aClass, moveDestination)
    maybeCompanion.foreach { companion =>
      movedClass.getContainingFile.add(companion)
      deleteClass(companion)
    }

    movedClass
  }

  def collectAssociations(@NotNull aClass: PsiClass, withCompanion: Boolean) {
    val alreadyMoved = getMoveDestination(aClass) == aClass.getContainingFile.getContainingDirectory
    aClass.getContainingFile match {
      case file: ScalaFile if !alreadyMoved =>
        applyWithCompanionModule(aClass, withCompanion) { clazz =>
          val range = clazz.getTextRange
          val associations = PROCESSOR.collectTransferableData(file, null,
            Array[Int](range.getStartOffset), Array[Int](range.getEndOffset))
          clazz.putCopyableUserData(ASSOCIATIONS_KEY, if (associations.isEmpty) null else associations.get(0))
        }
      case _ =>
    }
  }

  def restoreAssociations(@NotNull aClass: PsiClass, withCompanion: Boolean): Unit =
    applyWithCompanionModule(aClass, withCompanion) { clazz =>
      Option(clazz.getCopyableUserData(ASSOCIATIONS_KEY)).foreach {
        try {
          PROCESSOR.restoreAssociations(_, clazz.getContainingFile, clazz.getTextRange.getStartOffset, clazz.getProject)
        } finally {
          clazz.putCopyableUserData(ASSOCIATIONS_KEY, null)
        }
      }
    }

  def shiftAssociations(aClass: PsiClass, offsetChange: Int): Unit =
    aClass.getCopyableUserData(ASSOCIATIONS_KEY) match {
      case null =>
      case as: Associations =>  as.associations.foreach(a => a.range = a.range.shiftRight(offsetChange))
    }

  def saveMoveDestination(@NotNull element: PsiElement, moveDestination: PsiDirectory): Unit = {
    val classes = element match {
      case c: PsiClass => Seq(c)
      case f: ScalaFile => f.typeDefinitions
      case p: ScPackage => p.getClasses.toSeq
      case _ => Seq.empty
    }

    classes.foreach {
      applyWithCompanionModule(_, withCompanion = true) {
        _.putUserData(MOVE_DESTINATION, moveDestination)
      }
    }
  }

  def getMoveDestination(@NotNull element: PsiElement): PsiDirectory = element.getUserData[PsiDirectory](MOVE_DESTINATION)

  private def applyWithCompanionModule(clazz: PsiClass, withCompanion: Boolean)(function: PsiClass => Unit): Unit =
    (Option(clazz) ++ companionModule(clazz, withCompanion)).foreach(function)

  private def companionModule(clazz: PsiClass, withCompanion: Boolean): Option[ScTypeDefinition] =
    Option(clazz).collect {
      case definition: ScTypeDefinition if withCompanion => definition
    }.flatMap {
      getBaseCompanionModule
    }
}
