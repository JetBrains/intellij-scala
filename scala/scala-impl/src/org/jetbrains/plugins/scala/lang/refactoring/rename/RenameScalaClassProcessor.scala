package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.{PsiClass, PsiElement, PsiReference}
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.{RenameDialog, RenameJavaClassProcessor}
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.ScalaBytecodeConstants.TraitImplementationClassSuffix_211

import java.awt.BorderLayout
import java.util
import javax.swing.{JCheckBox, JComponent, JPanel}
import scala.jdk.CollectionConverters.SetHasAsScala

class RenameScalaClassProcessor extends RenameJavaClassProcessor with ScalaRenameProcessor {
  override def canProcessElement(element: PsiElement): Boolean = {
    element.is[ScTypeDefinition, PsiClassWrapper, ScTypeParam]
  }

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = element match {
    case PsiClassWrapper(definition) => definition
    case _ => element
  }

  override def findReferences(
    element: PsiElement,
    searchScope: SearchScope,
    searchInCommentsAndStrings: Boolean
  ): util.Collection[PsiReference] = {
    val references0 = super.findReferences(element, searchScope, searchInCommentsAndStrings)
    val references = ScalaRenameUtil.replaceImportClassReferences(references0)
    references
  }

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: util.Map[PsiElement, String]): Unit = {
    element match {
      case typeDef: ScTypeDefinition =>
        collectRenamesForTypeDefinition(typeDef, newName, allRenames)
      case _ =>
    }

    val allRenamedTypeDefs = allRenames.keySet.asScala.clone.filterByType[ScTypeDefinition]
    for {
      renamedTypeDef <- allRenamedTypeDefs
    } {
      fakeCompanionClassRename(renamedTypeDef, newName).foreach { case (clazz, newName) =>
        allRenames.put(clazz, newName)
      }
    }

    ScalaElementToRenameContributor.addAllElements(element, newName, allRenames)
  }

  private def fakeCompanionClassRename(typeDef: ScTypeDefinition, newName: String): Option[(PsiClass, String)] =
    typeDef match {
      case o: ScObject => o.fakeCompanionClass.map(_ -> newName)
      //Q: shouldn't we handle trait differently since scala 2.12? Does it matter in this code?
      case t: ScTrait  => Some(t.fakeCompanionClass -> (newName + TraitImplementationClassSuffix_211))
      case _           => None
    }

  private def collectRenamesForTypeDefinition(
    typeDefinition: ScTypeDefinition,
    newName: String,
    allRenames: util.Map[PsiElement, String]
  ): Unit = {
    val companion = ScalaPsiUtil.getCompanionModule(typeDefinition)
    companion match {
      case Some(companion) if ScalaApplicationSettings.getInstance.RENAME_COMPANION_MODULE =>
        allRenames.put(companion, newName)
      case _ =>
    }

    val file = typeDefinition.getContainingFile
    val containingFileHasSameNameAsToplevelDef = file != null && typeDefinition.isTopLevel && file.name == typeDefinition.name + ".scala"
    if (containingFileHasSameNameAsToplevelDef) {
      allRenames.put(file, s"$newName.scala")
    }
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

class ScalaClassRenameDialog(
  project: Project,
  psiElement: PsiElement,
  nameSuggestionContext: PsiElement,
  editor: Editor,
) extends RenameDialog(
  project,
  psiElement,
  nameSuggestionContext,
  editor,
) {

  // must be lazy, because it is used by super's constructor
  private lazy val chbRenameCompanion: JCheckBox = new JCheckBox("", true)

  override def createCenterPanel(): JComponent = {
    val companion = psiElement.asOptionOf[ScTypeDefinition].flatMap(_.baseCompanion) match {
      case Some(c) => c
      case None =>
        return null
    }

    val panel = Option(super.createCenterPanel()).getOrElse {
      new JPanel(new BorderLayout())
    }

    chbRenameCompanion.setText(ScalaBundle.message("rename.companion.module", companion.keywordPrefix))
    chbRenameCompanion.setSelected(true)
    panel.add(chbRenameCompanion, BorderLayout.WEST)

    panel
  }

  override def performRename(newName: String): Unit = {
    ScalaApplicationSettings.getInstance().RENAME_COMPANION_MODULE = chbRenameCompanion.isSelected
    super.performRename(newName)
    ScalaApplicationSettings.getInstance().RENAME_COMPANION_MODULE = true
  }
}
