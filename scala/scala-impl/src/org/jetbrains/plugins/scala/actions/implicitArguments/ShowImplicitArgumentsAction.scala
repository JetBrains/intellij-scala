package org.jetbrains.plugins.scala.actions.implicitArguments

import java.awt.event.MouseEvent
import java.awt.{BorderLayout, Dimension}

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.{JBPopupFactory, LightweightWindowEvent, JBPopup, JBPopupListener}
import com.intellij.openapi.util.{Disposer, Ref}
import com.intellij.psi.util.PsiUtilBase
import com.intellij.psi.{PsiFile, PsiElement, PsiWhiteSpace}
import com.intellij.ui.tree.{AsyncTreeModel, StructureTreeModel}
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.{ScrollPaneFactory, ClickListener}
import com.intellij.util.ArrayUtil
import javax.swing.tree.{DefaultMutableTreeNode, TreePath}
import javax.swing.{JPanel, JTree}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitArgumentsOwner, ScalaFile}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getExpression
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

/**
  * User: Alefas
  * Date: 25.10.11
  */

class ShowImplicitArgumentsAction extends AnAction(
  ScalaBundle.message("show.implicit.arguments.action.text"),
  ScalaBundle.message("show.implicit.arguments.action.description"),
  /* icon = */ null
) {
  import ShowImplicitArgumentsAction._

  override def update(e: AnActionEvent): Unit = ScalaActionUtil.enableAndShowIfInScalaFile(e)

  override def actionPerformed(e: AnActionEvent): Unit = {
    val context = e.getDataContext
    implicit val project: Project = CommonDataKeys.PROJECT.getData(context)
    implicit val editor: Editor = CommonDataKeys.EDITOR.getData(context)
    if (editor == null) return

    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    if (!file.isInstanceOf[ScalaFile]) return

    Stats.trigger(FeatureKey.showImplicitParameters)

    val targets = findAllTargets(file)

    if (targets.length == 0)
      ScalaActionUtil.showHint(editor, ScalaBundle.message("no.implicit.arguments"))
    else if (targets.length == 1)
      onChosen(targets(0))
    else
      ScalaRefactoringUtil.showChooserGeneric[ImplicitArgumentsTarget](
        editor, targets, onChosen, ScalaBundle.message("title.expressions"), _.presentation, _.expression
      )
  }

  private def onChosen(target: ImplicitArgumentsTarget)(implicit editor: Editor): Unit = {
    val range = target.expression.getTextRange

    val hadSelection = editor.getSelectionModel.hasSelection

    editor.getSelectionModel.setSelection(
      range.getStartOffset,
      range.getEndOffset
    )

    val popup = showPopup(editor, target.arguments, target.implicitConversion.nonEmpty)

    if (!hadSelection) {
      popup.addListener(new JBPopupListener {
        override def onClosed(event: LightweightWindowEvent): Unit = {
          editor.getSelectionModel.removeSelection()
        }
      })
    }

  }

  private def findAllTargets(file: PsiFile)(implicit editor: Editor, project: Project) = {
    if (editor.getSelectionModel.hasSelection)
      getExpression(file).toSeq.flatMap(allTargets).toArray
    else {
      val offset = editor.getCaretModel.getOffset
      val element: PsiElement = file.findElementAt(offset) match {
        case w: PsiWhiteSpace if w.getTextRange.getStartOffset == offset &&
          w.getText.contains("\n") => file.findElementAt(offset - 1)
        case p => p
      }
      element.withParentsInFile.toBuffer.flatMap(allTargets).toArray
    }
  }

  private def allTargets(element: PsiElement): Iterable[ImplicitArgumentsTarget] =
    implicitArgsNoConversion(element) ++ implicitArgsConversion(element)

  private def implicitArgsNoConversion(element: PsiElement): Option[ImplicitArgumentsTarget] = {
    element.asOptionOf[ImplicitArgumentsOwner]
      .flatMap(_.findImplicitArguments)
      .filter(_.nonEmpty)
      .map(ImplicitArgumentsTarget(element, _))
  }

  private def implicitArgsConversion(element: PsiElement): Option[ImplicitArgumentsTarget] =
    element.asOptionOf[ScExpression]
      .flatMap(_.implicitConversion())
      .filter(_.implicitParameters.nonEmpty)
      .map { srr =>
        ImplicitArgumentsTarget(element, srr.implicitParameters, Some(srr))
      }
}

object ShowImplicitArgumentsAction {
  private def getSelectedNode(jTree: JTree): AbstractTreeNode[_] = {
    val path: TreePath = jTree.getSelectionPath
    if (path != null) {
      var component: AnyRef = path.getLastPathComponent
      component match {
        case node: DefaultMutableTreeNode =>
          component = node.getUserObject
          component match {
            case abstractTreeNode: AbstractTreeNode[_] => return abstractTreeNode
            case _ =>
          }
        case _ =>
      }
    }
    null
  }

  private def navigateSelectedElement(popup: JBPopup, jTree: JTree, project: Project): Boolean = {
    val selectedNode: AbstractTreeNode[_] = getSelectedNode(jTree)

    val succeeded: Ref[Boolean] = new Ref[Boolean]
    val commandProcessor: CommandProcessor = CommandProcessor.getInstance
    commandProcessor.executeCommand(project, () => {
      if (selectedNode != null) {
        if (selectedNode.canNavigateToSource) {
          popup.cancel()
          selectedNode.navigate(true)
          succeeded.set(true)
        }
        else {
          succeeded.set(false)
        }
      }
      else {
        succeeded.set(false)
      }
      IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
    }, ScalaBundle.message("navigate"), null)
    succeeded.get
  }

  def showPopup(editor: Editor, results: Iterable[ScalaResolveResult], isConversion: Boolean): JBPopup = {
    val project = editor.getProject

    val jTree = new Tree()
    val structure = new ImplicitArgumentsTreeStructure(project, results)

    val tempDisposable = new Disposable {
      override def dispose(): Unit = ()
    }
    val structureTreeModel = new StructureTreeModel[ImplicitArgumentsTreeStructure](structure, tempDisposable)
    val asyncTreeModel = new AsyncTreeModel(structureTreeModel, true, tempDisposable)

    jTree.setModel(asyncTreeModel)
    jTree.setRootVisible(false)

    val minSize = jTree.getPreferredSize

    val scrollPane = ScrollPaneFactory.createScrollPane(jTree, true)

    val panel = new JPanel(new BorderLayout())

    panel.add(scrollPane, BorderLayout.CENTER)

    val F4: Array[Shortcut] =
      ActionManager.getInstance.getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet.getShortcuts
    val ENTER: Array[Shortcut] = CustomShortcutSet.fromString("ENTER").getShortcuts
    val shortcutSet: CustomShortcutSet = new CustomShortcutSet(ArrayUtil.mergeArrays(F4, ENTER): _*)

    val title = if (isConversion) ScalaBundle.message("implicit.arguments.for.implicit.conversion") else ScalaBundle.message("implicit.arguments")

    val popup: JBPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, jTree).
      setRequestFocus(true).
      setResizable(true).
      setTitle(title).
      setMinSize(new Dimension(minSize.width + 700, minSize.height)).
      createPopup

    new AnAction {
      override def actionPerformed(e: AnActionEvent): Unit = {
        val succeeded: Boolean = navigateSelectedElement(popup, jTree, project)
        if (succeeded) {
          unregisterCustomShortcutSet(panel)
        }
      }
    }.registerCustomShortcutSet(shortcutSet, panel)

    new ClickListener {
      override def onClick(e: MouseEvent, clickCount: Int): Boolean = {
        val path: TreePath = jTree.getPathForLocation(e.getX, e.getY)
        if (path == null) return false
        navigateSelectedElement(popup, jTree, project)
        true
      }
    }.installOn(jTree)

    Disposer.register(popup, tempDisposable)

    popup.showInBestPositionFor(editor)
    popup
  }
}