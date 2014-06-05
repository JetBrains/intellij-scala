package org.jetbrains.plugins.scala
package lang.refactoring.move

import com.intellij.psi.{PsiElement, PsiClass, PsiDirectory, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScClass, ScObject, ScTypeDefinition}
import com.intellij.openapi.util.{TextRange, Key}
import com.intellij.psi.util.PsiTreeUtil
import scala.collection.JavaConverters._
import org.jetbrains.plugins.scala.lang.refactoring.util.{ScalaDirectoryService, ScalaNamesUtil}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaFile}
import com.intellij.psi.javadoc.PsiDocComment
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.conversion.copy.{Associations, ScalaCopyPastePostProcessor}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil

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
        val noCaseClasses = classes.collect{case c: ScClass if c.isCase => c}.isEmpty
        classes.count(_.isInstanceOf[ScObject]) == 1 && noCaseClasses
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
    def deleteClass(aClass: PsiClass) = {
      aClass.getContainingFile match {
        case file: ScalaFile =>
          file.typeDefinitions match {
            case Seq(`aClass`) if !file.isScriptFile() && !file.isWorksheetFile =>
              file.delete()
              fileWasDeleted = true
            case _ => aClass.delete()
          }
      }
    }
    def moveClassInner(aClass: PsiClass, moveDestination: PsiDirectory): PsiClass = {
      var newClass: PsiClass = null
      (aClass, aClass.getContainingFile) match {
        case (td: ScTypeDefinition, file: ScalaFile) =>
          val fileWithOldFileName = moveDestination.findFile(file.getName)
          val className = td.name
          val fileWithClassName = moveDestination.findFile(className + "." + ScalaFileType.DEFAULT_EXTENSION)
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
              case _: ScClass => "Scala Class"
              case _: ScTrait => "Scala Trait"
              case _: ScObject => "Scala Object"
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

    if (withCompanion) ScalaPsiUtil.getBaseCompanionModule(aClass).foreach(c => moveClassInner(c, moveDestination))
    moveClassInner(aClass, moveDestination)
  }

  def collectAssociations(@NotNull aClass: PsiClass, withCompanion: Boolean) {
    def collectData(clazz: PsiClass, file: ScalaFile) {
      val range: TextRange = clazz.getTextRange
      val associations = PROCESSOR.collectTransferableData(file, null,
        Array[Int](range.getStartOffset), Array[Int](range.getEndOffset))
      clazz.putCopyableUserData(ASSOCIATIONS_KEY, associations)
    }
    val alreadyMoved = getMoveDestination(aClass) == aClass.getContainingFile.getContainingDirectory
    aClass.getContainingFile match {
      case file: ScalaFile if !alreadyMoved =>
        collectData(aClass, file)
        if (withCompanion)
          ScalaPsiUtil.getBaseCompanionModule(aClass).foreach(c => collectData(c, file))
      case _ =>
    }
  }

  def restoreAssociations(@NotNull aClass: PsiClass, withCompanion: Boolean) {
    def restoreInner(clazz: PsiClass) {
      val associations: Associations = clazz.getCopyableUserData(ASSOCIATIONS_KEY)
      if (associations != null) {
        try {
          PROCESSOR.restoreAssociations(associations, clazz.getContainingFile, clazz.getTextRange.getStartOffset, clazz.getProject)
        }
        finally {
          clazz.putCopyableUserData(ASSOCIATIONS_KEY, null)
        }
      }
    }
    restoreInner(aClass)
    if (withCompanion)
      ScalaPsiUtil.getBaseCompanionModule(aClass).foreach(restoreInner)
  }

  def shiftAssociations(aClass: PsiClass, offsetChange: Int) {
    aClass.getCopyableUserData(ASSOCIATIONS_KEY) match {
      case null =>
      case as: Associations =>  as.associations.foreach(a => a.range = a.range.shiftRight(offsetChange))
    }
  }

  def saveMoveDestination(@NotNull element: PsiElement, moveDestination: PsiDirectory) = {
    val classes = element match {
      case c: PsiClass => Seq(c)
      case f: ScalaFile => f.typeDefinitions
      case p: ScPackage => p.getClasses.toSeq
      case _ => Nil
    }
    classes.flatMap{
      case td: ScTypeDefinition => td :: ScalaPsiUtil.getBaseCompanionModule(td).toList
      case e => List(e)
    }.foreach(_.putUserData(MOVE_DESTINATION, moveDestination))
  }

  def getMoveDestination(@NotNull element: PsiElement): PsiDirectory = element.getUserData[PsiDirectory](MOVE_DESTINATION)
}
