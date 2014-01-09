package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.refactoring.rename.{RenameDialog, RenameJavaClassProcessor}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi.PsiElement
import annotation.tailrec
import java.util
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import com.intellij.openapi.project.Project
import javax.swing.{JPanel, JComponent, JCheckBox}
import org.jetbrains.plugins.scala.ScalaBundle
import java.awt.BorderLayout
import com.intellij.usageView.UsageInfo
import com.intellij.refactoring.listeners.RefactoringElementListener

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.09.2009
 */

class RenameScalaClassProcessor extends RenameJavaClassProcessor {
  override def canProcessElement(element: PsiElement): Boolean = {
    element.isInstanceOf[ScTypeDefinition] || element.isInstanceOf[PsiClassWrapper] || element.isInstanceOf[ScTypeParam]
  }

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = {
    element match {
      case wrapper: PsiClassWrapper => wrapper.definition
      case _ => element
    }
  }

  override def findReferences(element: PsiElement) = ScalaRenameUtil.replaceImportClassReferences(ScalaRenameUtil.findReferences(element))

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: util.Map[PsiElement, String]) {
    element match {
      case td: ScTypeDefinition =>
        ScalaPsiUtil.getCompanionModule(td) match {
          case Some(companion) if ScalaApplicationSettings.getInstance().RENAME_COMPANION_MODULE => allRenames.put(companion, newName)
          case _ =>
        }
        @tailrec
        def isTop(element: PsiElement): Boolean = {
          element match {
            case null => true
            case td: ScTemplateDefinition => false
            case _ => isTop(element.getContext)
          }
        }
        val file = td.getContainingFile
        if (file != null && isTop(element.getContext) && file.name == td.name + ".scala") {
          allRenames.put(file, newName + ".scala")
        }
      case docTagParam: ScTypeParam =>
        docTagParam.owner match {
          case commentOwner: ScDocCommentOwner =>
            commentOwner.getDocComment match {
              case comment: ScDocComment =>
                comment.findTagsByName(MyScaladocParsing.TYPE_PARAM_TAG).foreach {
                  b => if (b.getValueElement != null && b.getValueElement.getText == docTagParam.name)
                    allRenames.put(b.getValueElement, newName)
                }
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }

    //put rename for fake object companion class
    def addLightClasses(element: PsiElement) {
      element match {
        case o: ScObject =>
          o.fakeCompanionClass match {
            case Some(clazz) => allRenames.put(clazz, newName)
            case None =>
          }
        case t: ScTrait =>
          allRenames.put(t.fakeCompanionClass, newName + "$class")
        case _ =>
      }
    }

    import scala.collection.JavaConverters.asScalaSetConverter
    val elems = allRenames.keySet().asScala.clone()
    elems.foreach(addLightClasses)

    ScalaElementToRenameContributor.getAll(element, newName, allRenames)
  }

  override def getElementToSearchInStringsAndComments(element: PsiElement): PsiElement = {
    element match {
      case o: ScObject => o.fakeCompanionClassOrCompanionClass
      case wrapper: PsiClassWrapper => wrapper.definition match {
        case o: ScObject => wrapper
        case definition => definition
      }
      case _ => element
    }
  }

  override def setToSearchInComments(element: PsiElement, enabled: Boolean) {
    ScalaApplicationSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_AND_STRINGS = enabled
  }

  override def isToSearchInComments(psiElement: PsiElement): Boolean = {
    ScalaApplicationSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_AND_STRINGS
  }

  override def createRenameDialog(project: Project, element: PsiElement, nameSuggestionContext: PsiElement, editor: Editor): RenameDialog =
    new ScalaClassRenameDialog(project, element, nameSuggestionContext, editor)

  override def renameElement(element: PsiElement, newName: String, usages: Array[UsageInfo], listener: RefactoringElementListener) {
    ScalaRenameUtil.doRenameGenericNamedElement(element, newName, usages, listener)
  }
}

class ScalaClassRenameDialog(project: Project, psiElement: PsiElement, nameSuggestionContext: PsiElement, editor: Editor)
        extends {
          private val chbRenameCompanion: JCheckBox = new JCheckBox("", true)
        }
        with RenameDialog(project: Project, psiElement: PsiElement, nameSuggestionContext: PsiElement, editor: Editor) {

  override def createCenterPanel(): JComponent = {

    val companionType: Option[String] = psiElement match {
      case clazz: ScTypeDefinition =>
        ScalaPsiUtil.getBaseCompanionModule(clazz) match {
          case Some(_: ScObject) => Some("object")
          case Some(_: ScTrait) => Some("trait")
          case Some(_: ScClass) => Some("class")
          case _ => None
        }
      case _ => None
    }

    if (companionType.isDefined) {
      chbRenameCompanion.setText(ScalaBundle.message("rename.companion.module", companionType.get))
      chbRenameCompanion.setSelected(true)
      val panel = Option(super.createCenterPanel()).getOrElse(new JPanel(new BorderLayout()))
      panel.add(chbRenameCompanion, BorderLayout.WEST)
      panel
    }
    else null
  }

  override def performRename(newName: String) {
    ScalaApplicationSettings.getInstance().RENAME_COMPANION_MODULE = chbRenameCompanion.isSelected
    super.performRename(newName)
    ScalaApplicationSettings.getInstance().RENAME_COMPANION_MODULE = true
  }
}