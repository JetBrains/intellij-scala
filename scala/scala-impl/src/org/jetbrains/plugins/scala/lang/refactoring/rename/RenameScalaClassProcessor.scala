package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.{RenameDialog, RenameJavaClassProcessor}
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import java.awt.BorderLayout
import java.util
import javax.swing.{JCheckBox, JComponent, JPanel}
import scala.annotation.tailrec

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.09.2009
 */

class RenameScalaClassProcessor extends RenameJavaClassProcessor with ScalaRenameProcessor {
  override def canProcessElement(element: PsiElement): Boolean = {
    element.is[ScTypeDefinition, PsiClassWrapper, ScTypeParam]
  }

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = element match {
    case PsiClassWrapper(definition) => definition
    case _ => element
  }

  override def findReferences(element: PsiElement,
                              searchScope: SearchScope,
                              searchInCommentsAndStrings: Boolean): util.Collection[PsiReference] =
    ScalaRenameUtil.replaceImportClassReferences(super.findReferences(element, searchScope, searchInCommentsAndStrings))

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: util.Map[PsiElement, String]): Unit = {
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
            case _: ScTemplateDefinition => false
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
                  b => if (b.getValueElement != null && b.getValueElement.textMatches(docTagParam.name))
                    allRenames.put(b.getValueElement, newName)
                }
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }

    //put rename for fake object companion class
    def addLightClasses(element: PsiElement): Unit = {
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

    import scala.jdk.CollectionConverters._
    val elems = allRenames.keySet().asScala.clone()
    elems.foreach(addLightClasses)

    ScalaElementToRenameContributor.addAllElements(element, newName, allRenames)
  }

  override def getElementToSearchInStringsAndComments(element: PsiElement): PsiElement = {
    element match {
      case o: ScObject => o.fakeCompanionClassOrCompanionClass
      case PsiClassWrapper(_: ScObject) => element
      case PsiClassWrapper(definition) => definition
      case _ => element
    }
  }

  override def createRenameDialog(project: Project, element: PsiElement, nameSuggestionContext: PsiElement, editor: Editor): RenameDialog =
    new ScalaClassRenameDialog(project, element, nameSuggestionContext, editor)

  override def renameElement(element: PsiElement, newName: String, usages: Array[UsageInfo], listener: RefactoringElementListener): Unit = {
    ScalaRenameUtil.doRenameGenericNamedElement(element, newName, usages, listener)
  }
}

class ScalaClassRenameDialog(project: Project, psiElement: PsiElement, nameSuggestionContext: PsiElement, editor: Editor)
        extends RenameDialog(project: Project, psiElement: PsiElement, nameSuggestionContext: PsiElement, editor: Editor) {

  // must be lazy, because it is used by super's constructor
  private lazy val chbRenameCompanion: JCheckBox = new JCheckBox("", true)
  
  override def createCenterPanel(): JComponent = {
    val companion = psiElement.asOptionOf[ScTypeDefinition].flatMap(_.baseCompanion)

    companion.collect {
      case _: ScObject => "object"
      case _: ScTrait => "trait"
      case _: ScClass => "class"
    }.foreach { text =>
      chbRenameCompanion.setText(ScalaBundle.message("rename.companion.module", text))
      chbRenameCompanion.setSelected(true)
    }

    companion.map { _ =>
      val panel = Option(super.createCenterPanel()).getOrElse {
        new JPanel(new BorderLayout())
      }
      panel.add(chbRenameCompanion, BorderLayout.WEST)
      panel
    }.orNull
  }

  override def performRename(newName: String): Unit = {
    ScalaApplicationSettings.getInstance().RENAME_COMPANION_MODULE = chbRenameCompanion.isSelected
    super.performRename(newName)
    ScalaApplicationSettings.getInstance().RENAME_COMPANION_MODULE = true
  }
}
