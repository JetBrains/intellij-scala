package org.jetbrains.plugins.scala
package lang
package refactoring

import com.intellij.openapi.util.Key
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiDirectory, PsiElement, PsiFile}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.conversion.copy.{Associations, ScalaCopyPastePostProcessor}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaFile}

import scala.collection.JavaConverters

package object move {

  private val PROCESSOR = new ScalaCopyPastePostProcessor
  private val ASSOCIATIONS_KEY: Key[Associations] = Key.create("ASSOCIATIONS")

  object MoveDestination {

    private val key = Key.create[PsiDirectory]("MoveDestination")

    def apply(element: PsiElement): PsiDirectory = element.getUserData(key)

    def update(element: PsiElement, destination: PsiDirectory): Unit = {
      element.putUserData(key, destination)
    }
  }

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
    import JavaConverters._

    val allClasses = PsiTreeUtil.findChildrenOfType(file, classOf[ScTypeDefinition])
    val withSameName = allClasses.asScala.filter(_.name == util.ScalaNamesUtil.scalaName(aClass))
    withSameName.size == 1 && canBeCompanions(withSameName.head, aClass) || withSameName.isEmpty
  }

  def doMoveClass(@NotNull aClass: PsiClass, @NotNull moveDestination: PsiDirectory): PsiClass = {
    var fileWasDeleted: Boolean = false
    val maybeCompanion = companionModule(aClass, moveCompanion)

    def deleteClass(aClass: PsiClass): Unit = aClass.getContainingFile match {
      case file: ScalaFile =>
        file.typeDefinitions match {
          case Seq(`aClass`) if !file.isScriptFile && !file.isWorksheetFile =>
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
            import actions.ScalaFileTemplateUtil._
            val template: String = td match {
              case _: ScClass => SCALA_CLASS
              case _: ScTrait => SCALA_TRAIT
              case _: ScObject => SCALA_OBJECT
            }

            val created = util.ScalaDirectoryService.createClassFromTemplate(moveDestination, td.name, template, askToDefineVariables = false)
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
      newClass
    }

    val movedClass = moveClassInner(aClass, moveDestination)
    maybeCompanion.foreach { companion =>
      movedClass.getContainingFile.add(companion)
      deleteClass(companion)
    }

    movedClass
  }

  def collectAssociations(@NotNull clazz: PsiClass): Unit =
    collectAssociations(clazz, moveCompanion)

  def collectAssociations(@NotNull clazz: PsiClass, withCompanion: Boolean): Unit =
    clazz.getContainingFile match {
      case file: ScalaFile if file.getContainingDirectory != MoveDestination(clazz) =>
        applyWithCompanionModule(clazz, withCompanion) { clazz =>
          val range = clazz.getTextRange
          val associations = PROCESSOR.collectTransferableData(file, null,
            Array[Int](range.getStartOffset), Array[Int](range.getEndOffset))
          clazz.putCopyableUserData(ASSOCIATIONS_KEY, if (associations.isEmpty) null else associations.get(0))
        }
      case _ =>
    }

  def restoreAssociations(@NotNull aClass: PsiClass): Unit =
    applyWithCompanionModule(aClass, moveCompanion) { clazz =>
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
      case as: Associations => as.associations.foreach(a => a.range = a.range.shiftRight(offsetChange))
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
        MoveDestination(_) = moveDestination
      }
    }
  }

  private def applyWithCompanionModule(clazz: PsiClass, withCompanion: Boolean)
                                      (function: PsiClass => Unit): Unit =
    (Option(clazz) ++ companionModule(clazz, withCompanion)).foreach(function)

  def companionModule(clazz: PsiClass, withCompanion: Boolean): Option[ScTypeDefinition] =
    Option(clazz).collect {
      case definition: ScTypeDefinition if withCompanion => definition
    }.flatMap {
      _.baseCompanionModule
    }

  private def moveCompanion: Boolean = settings.ScalaApplicationSettings.getInstance.MOVE_COMPANION
}
