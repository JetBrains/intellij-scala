package org.jetbrains.plugins.scala.actions

import java.awt.event.MouseEvent
import java.awt.{BorderLayout, Dimension}
import java.util
import javax.swing.tree.{DefaultMutableTreeNode, DefaultTreeModel, TreePath}
import javax.swing.{JPanel, JTree}

import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.ide.util.treeView.{AbstractTreeBuilder, AbstractTreeNode, AbstractTreeStructure, NodeDescriptor}
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.{JBPopup, JBPopupFactory}
import com.intellij.openapi.util.{Disposer, Ref}
import com.intellij.psi.util.PsiUtilBase
import com.intellij.psi.{PsiElement, PsiManager, PsiNamedElement, PsiWhiteSpace}
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.{ClickListener, ScrollPaneFactory}
import com.intellij.util.ArrayUtil
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScEarlyDefinitions}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.mutable.ArrayBuffer

/**
 * User: Alefas
 * Date: 25.10.11
 */

class ShowImplicitParametersAction extends AnAction("Show implicit parameters action") {
  override def update(e: AnActionEvent) {
    ScalaActionUtil.enableAndShowIfInScalaFile(e)
  }

  private def presentableText(rr: ScalaResolveResult, context: ScExpression): String = {
    val named = rr.getElement
    ScalaPsiUtil.nameContext(named).getContext match {
      case _: ScTemplateBody | _: ScEarlyDefinitions =>
        rr.fromType match {
          case Some(tp) => named.name //todo:
          case None => named.name //todo:
        }
      //Local value
      case _ => named.name
    }
  }
  
  private def implicitParams(expr: PsiElement): Option[Seq[ScalaResolveResult]] = {
    def checkTypeElement(element: ScTypeElement): Option[Option[scala.Seq[ScalaResolveResult]]] = {
      def checkSimpleType(s: ScSimpleTypeElement) = {
        s.findImplicitParameters
      }
      element match {
        case s: ScSimpleTypeElement =>
          return Some(checkSimpleType(s))
        case p: ScParameterizedTypeElement =>
          p.typeElement match {
            case s: ScSimpleTypeElement =>
              return Some(checkSimpleType(s))
            case _ =>
          }
        case _ =>
      }
      None
    }
    expr match {
      case expr: ScNewTemplateDefinition =>
        expr.extendsBlock.templateParents match {
          case Some(tp) =>
            val elements = tp.typeElements
            if (elements.length > 0) {
              checkTypeElement(elements(0)) match {
                case Some(x) => return x
                case None =>
              }
            }
          case _ =>
        }
      case expr: ScExpression =>
        return expr.findImplicitParameters
      case constr: ScConstructor =>
        checkTypeElement(constr.typeElement) match {
          case Some(x) => return x
          case _ =>
        }
      case _ =>
    }
    None
  }

  def actionPerformed(e: AnActionEvent) {
    val context = e.getDataContext
    val project = CommonDataKeys.PROJECT.getData(context)
    val editor = CommonDataKeys.EDITOR.getData(context)
    if (editor == null) return
    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    if (!file.isInstanceOf[ScalaFile]) return

    def forExpr(expr: PsiElement) {
      val implicitParameters = implicitParams(expr)
      implicitParameters match {
        case None | Some(Seq()) =>
          ScalaActionUtil.showHint(editor, "No implicit parameters")
        case Some(seq) => showPopup(editor, seq)
      }
    }

    if (editor.getSelectionModel.hasSelection) {
      val selectionStart = editor.getSelectionModel.getSelectionStart
      val selectionEnd = editor.getSelectionModel.getSelectionEnd
      val opt = ScalaRefactoringUtil.getExpression(project, editor, file, selectionStart, selectionEnd)
      opt match {
        case Some((expr, _)) =>
          forExpr(expr)
          return
        case _ => return
      }
    } else {
      val offset = editor.getCaretModel.getOffset
      val element: PsiElement = file.findElementAt(offset) match {
        case w: PsiWhiteSpace if w.getTextRange.getStartOffset == offset &&
          w.getText.contains("\n") => file.findElementAt(offset - 1)
        case p => p
      }
      def getExpressions: Array[PsiElement] = {
        val res = new ArrayBuffer[PsiElement]
        var parent = element
        while (parent != null) {
          implicitParams(parent) match {
            case Some(seq) if seq.length > 0 =>
              parent match {
                case constr: ScConstructor =>
                  var p = constr.getParent
                  if (p != null) p = p.getParent
                  if (p != null) p = p.getParent
                  if (!p.isInstanceOf[ScNewTemplateDefinition]) res += parent
                case _ =>
                  res += parent
              }
            case _ =>
          }
          parent = parent.getParent
        }
        res.toArray
      }
      val expressions = getExpressions
      def chooseExpression(expr: PsiElement) {
        editor.getSelectionModel.setSelection(expr.getTextRange.getStartOffset,
          expr.getTextRange.getEndOffset)
        forExpr(expr)
      }
      if (expressions.length == 0) {
        ScalaActionUtil.showHint(editor, "No implicit parameters")
        return
      } else if (expressions.length == 1) {
        chooseExpression(expressions(0))
      } else {
        ScalaRefactoringUtil.showChooser(editor, expressions, elem =>
          chooseExpression(elem), "Expressions", (expr: PsiElement) => {
          expr match {
            case expr: ScExpression =>
              ScalaRefactoringUtil.getShortText(expr)
            case _ => expr.getText.slice(0, 20)
          }
        })
      }
    }
  }

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
    commandProcessor.executeCommand(project, new Runnable {
      def run(): Unit = {
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
      }
    }, "Navigate", null)
    succeeded.get
  }

  private def showPopup(editor: Editor, results: Seq[ScalaResolveResult]): Unit = {
    val project = editor.getProject

    val tree = new Tree()
    val structure = new ImplicitParametersTreeStructure(project, results)
    val builder = new AbstractTreeBuilder(tree, new DefaultTreeModel(new DefaultMutableTreeNode), structure, null) {
      override def isSmartExpand: Boolean = false
    }

    val jTree = builder.getTree

    jTree.setRootVisible(false)
    val minSize = jTree.getPreferredSize

    val scrollPane = ScrollPaneFactory.createScrollPane(jTree, true)

    val panel = new JPanel(new BorderLayout())

    panel.add(scrollPane, BorderLayout.CENTER)

    val F4: Array[Shortcut] =
      ActionManager.getInstance.getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet.getShortcuts
    val ENTER: Array[Shortcut] = CustomShortcutSet.fromString("ENTER").getShortcuts
    val shortcutSet: CustomShortcutSet = new CustomShortcutSet(ArrayUtil.mergeArrays(F4, ENTER): _*)


    val popup: JBPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, jTree).
      setRequestFocus(true).
      setResizable(true).
      setTitle("Implicit parameters:").
      setMinSize(new Dimension(minSize.width + 500, minSize.height)).
      createPopup


    new AnAction {
      def actionPerformed(e: AnActionEvent) {
        val succeeded: Boolean = navigateSelectedElement(popup, jTree, project)
        if (succeeded) {
          unregisterCustomShortcutSet(panel)
        }
      }
    }.registerCustomShortcutSet(shortcutSet, panel)

    new ClickListener {
      def onClick(e: MouseEvent, clickCount: Int): Boolean = {
        val path: TreePath = jTree.getPathForLocation(e.getX, e.getY)
        if (path == null) return false
        navigateSelectedElement(popup, jTree, project)
        true
      }
    }.installOn(jTree)

    Disposer.register(popup, builder)

    popup.showInBestPositionFor(editor)
  }
}

class ImplicitParametersTreeStructure(project: Project,
                                      results: Seq[ScalaResolveResult]) extends AbstractTreeStructure {
  private val manager = PsiManager.getInstance(project)

  class ImplicitParametersNode(_value: ScalaResolveResult)
    extends {
      val value = if (_value == null) new ScalaResolveResult(ScalaPsiElementFactory.createParameterFromText("NotFoundParameter: Int", manager)) else _value
    } with AbstractPsiBasedNode[ScalaResolveResult](project, value, ViewSettings.DEFAULT) {
    override def extractPsiFromValue(): PsiNamedElement = value.getElement

    override def getChildrenImpl: util.Collection[AbstractTreeNode[_]] = {
      val list = new util.ArrayList[AbstractTreeNode[_]]()
      value.implicitParameters.foreach {
        case result => list.add(new ImplicitParametersNode(result))
      }
      list
    }

    override def updateImpl(data: PresentationData): Unit = {
      val namedElement = extractPsiFromValue()
      if (namedElement != null) {
        val text: String = namedElement.name
        if (text == "NotFoundParameter") {
          data.setPresentableText("Parameter not found")
          data.setAttributesKey(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)
        } else {
          namedElement match {
            case s: ScNamedElement =>
              val presentation = s.getPresentation
              data.setLocationString(presentation.getLocationString.drop(1).dropRight(1))
              data.setPresentableText(presentation.getPresentableText)
            case _ => data.setPresentableText(text)
          }
        }
      }
    }

    override def equals(obj: Any): Boolean = {
      obj match {
        case ref: AnyRef => this eq ref
        case _ => false
      }
    }
  }

  private class RootNode extends AbstractTreeNode[Any](project, ()) {
    override def getChildren: util.Collection[_ <: AbstractTreeNode[_]] = {
      val list = new util.ArrayList[AbstractTreeNode[_]]()
      results.foreach { result => list.add(new ImplicitParametersNode(result)) }
      list
    }

    override def update(presentation: PresentationData): Unit = {}
  }

  override def getRootElement: AnyRef = new RootNode

  override def getParentElement(p1: Any): AnyRef = null

  override def getChildElements(p1: Any): Array[AnyRef] = {
    p1 match {
      case n: ImplicitParametersNode =>
        val childrenImpl = n.getChildrenImpl
        childrenImpl.toArray(new Array[AnyRef](childrenImpl.size))
      case _: RootNode => results.map(new ImplicitParametersNode(_)).toArray
      case _ => Array.empty
    }
  }

  override def createDescriptor(obj: Any, parent: NodeDescriptor[_]): NodeDescriptor[_] = {
    obj.asInstanceOf[NodeDescriptor[_]]
  }

  override def hasSomethingToCommit: Boolean = false

  override def commit(): Unit = {}
}