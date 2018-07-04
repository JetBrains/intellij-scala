package org.jetbrains.plugins.scala.actions

import java.awt.event.MouseEvent
import java.awt.{BorderLayout, Dimension}
import java.util

import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.ide.util.treeView.{AbstractTreeBuilder, AbstractTreeNode, AbstractTreeStructure, NodeDescriptor}
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.{JBPopup, JBPopupFactory, JBPopupListener, LightweightWindowEvent}
import com.intellij.openapi.util.{Disposer, Ref}
import com.intellij.psi.util.PsiUtilBase
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement, PsiWhiteSpace}
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.{ClickListener, ScrollPaneFactory}
import com.intellij.util.ArrayUtil
import javax.swing.tree.{DefaultMutableTreeNode, DefaultTreeModel, TreePath}
import javax.swing.{Icon, JPanel, JTree}
import org.jetbrains.plugins.scala.actions.ShowImplicitArgumentsAction._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getExpression
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

/**
 * User: Alefas
 * Date: 25.10.11
 */

class ShowImplicitArgumentsAction extends AnAction("Show implicit arguments action") {
  override def update(e: AnActionEvent) {
    ScalaActionUtil.enableAndShowIfInScalaFile(e)
  }

  def actionPerformed(e: AnActionEvent) {
    val context = e.getDataContext
    implicit val project: Project = CommonDataKeys.PROJECT.getData(context)
    implicit val editor: Editor = CommonDataKeys.EDITOR.getData(context)
    if (editor == null) return

    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    if (!file.isInstanceOf[ScalaFile]) return

    Stats.trigger(FeatureKey.showImplicitParameters)

    val targets = findAllTargets(file)

    if (targets.length == 0)
      ScalaActionUtil.showHint(editor, "No implicit arguments")
    else if (targets.length == 1)
      onChosen(targets(0))
    else
      ScalaRefactoringUtil.showChooserGeneric[ImplicitArgumentsTarget](
        editor, targets, onChosen, "Expressions", _.presentation, _.expression
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
    implicitParams(element) match {
      case Some(seq) if seq.nonEmpty =>
        element match {
          case constr: ScConstructor =>
            var p = constr.getParent
            if (p != null) p = p.getParent
            if (p != null) p = p.getParent
            if (!p.isInstanceOf[ScNewTemplateDefinition])
              Some(ImplicitArgumentsTarget(element, seq))
            else None
          case _ =>
            Some(ImplicitArgumentsTarget(element, seq))
        }
      case _ => None
    }
  }

  private def implicitArgsConversion(element: PsiElement): Option[ImplicitArgumentsTarget] =
    element.asOptionOf[ScExpression]
      .flatMap(_.implicitConversion())
      .flatMap { srr =>
        if (srr.implicitParameters.isEmpty) None
        else Some {
          ImplicitArgumentsTarget(element, srr.implicitParameters, Some(srr))
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

  private def showPopup(editor: Editor, results: Seq[ScalaResolveResult], isConversion: Boolean): JBPopup = {
    implicit val project = editor.getProject

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

    val title = if (isConversion) "Implicit arguments for implicit conversion:" else "Implicit arguments:"

    val popup: JBPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, jTree).
      setRequestFocus(true).
      setResizable(true).
      setTitle(title).
      setMinSize(new Dimension(minSize.width + 700, minSize.height)).
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
    popup
  }

  private case class ImplicitArgumentsTarget(expression: PsiElement,
                                             arguments: Seq[ScalaResolveResult],
                                             implicitConversion: Option[ScalaResolveResult] = None) {
    def presentation: String = {
      val shortenedText = expression match {
        case e: ScExpression => ScalaRefactoringUtil.getShortText(e)
        case _ => expression.getText.take(20)
      }
      implicitConversion match {
        case None => shortenedText
        case Some(c) => c.element.name + s"($shortenedText) //implicit conversion"
      }
    }
  }
}

// TODO Should probably be handled by the ImplicitParametersOwner, or at least extracted into an utility method
object ShowImplicitArgumentsAction {
  def implicitParams(expr: PsiElement): Option[Seq[ScalaResolveResult]] = {
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
            if (elements.nonEmpty) {
              checkTypeElement(elements.head) match {
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

  def missingImplicitArgumentIn(result: ScalaResolveResult): Option[Option[ScType]] = {
    result.isImplicitParameterProblem
      .option(result.implicitSearchState.map(_.tp))
  }
}

class ImplicitParametersTreeStructure(project: Project,
                                      results: Seq[ScalaResolveResult])
  extends AbstractTreeStructure {

  implicit def ctx: ProjectContext = project

  class ImplicitParametersNode(value: ScalaResolveResult, implicitResult: Option[ImplicitResult] = None)

    extends AbstractPsiBasedNode[ScalaResolveResult](project, value, ViewSettings.DEFAULT) {

    override def extractPsiFromValue(): PsiNamedElement = value.getElement

    override def getChildrenImpl: util.Collection[AbstractTreeNode[_]] = {
      val list = new util.ArrayList[AbstractTreeNode[_]]()

      if (value.isImplicitParameterProblem) {
        def addErrorLeaf(errorText: String): Boolean = {
          list.add(new AbstractTreeNode[String](project, errorText) {
            override def getChildren: util.Collection[AbstractTreeNode[_]] = new util.ArrayList[AbstractTreeNode[_]]()

            override def update(data: PresentationData): Unit = {
              data.setPresentableText(errorText)
              data.setAttributesKey(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)
            }
          })
        }
        value.implicitSearchState match {
          case Some(state) =>
            val collector = new ImplicitCollector(state.copy(fullInfo = true))
            collector.collect().foreach { r =>
              r.implicitReason match {
                case TypeDoesntConformResult =>
                case BadTypeResult =>
                case CantFindExtensionMethodResult =>
                case FunctionForParameterResult =>
                case implicitRes => list.add(new ImplicitParametersNode(r, Some(implicitRes)))
              }
            }
            if (list.isEmpty) addErrorLeaf("Applicable by type implicits were not found")
          case _ =>
            addErrorLeaf("No information for no reason")
        }
      } else {
        value.implicitParameters.foreach(result => list.add(new ImplicitParametersNode(result)))
      }

      list
    }

    override def updateImpl(data: PresentationData): Unit = {
      presentationAttributes.foreach(data.setAttributesKey)
      presentationIcon      .foreach(data.setIcon)
      presentationTooltip   .foreach(data.setTooltip)
      locationString        .foreach(data.setLocationString)

      data.setPresentableText(presentableText)
    }

    private val presentableText = presentationText(value, implicitResult)

    private def presentationIcon: Option[Icon] =
      if (value.isImplicitParameterProblem) None
      else value.element.getIcon(0).toOption

    private def presentationAttributes: Option[TextAttributesKey] = {
      if (value.isImplicitParameterProblem)
        return Some(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)

      implicitResult.collect {
        case DivergedImplicitResult          => CodeInsightColors.ERRORS_ATTRIBUTES
        case CantInferTypeParameterResult    => CodeInsightColors.ERRORS_ATTRIBUTES
        case ImplicitParameterNotFoundResult => CodeInsightColors.ERRORS_ATTRIBUTES
      }
    }

    private def presentationTooltip: Option[String] = implicitResult.collect {
      case OkResult                        => "Implicit argument is applicable"
      case DivergedImplicitResult          => "Implicit is diverged"
      case CantInferTypeParameterResult    => "Can't infer proper types for type parameters"
      case ImplicitParameterNotFoundResult => "Can't find implicit argument for this definition"
    }

    private def locationString: Option[String] = {
      if (value.isImplicitParameterProblem) return None

      val description = value.element.nameContext match {
        case p: ScParameter =>
          p.owner match {
            case f: ScFunctionDefinition => s"parameter of ${f.name}"
            case c: ScPrimaryConstructor => s"parameter of ${c.getClassNameText}"
            case _                       => ""
          }
        case ContainingClass(c) =>
          val className = c.getPresentation.getPresentableText
          c match {
            case _: ScClass => "class " + className
            case _: ScTrait => "trait " + className
            case o: ScObject => if (o.isPackageObject) "package object " + className else "object " + className
            case _ => "anonymous class"
          }
        case m: ScMember if m.isLocal =>
          val owner = m.contexts.take(2).collectFirst {
            case named: ScNamedElement => named.name
            case d: ScDeclaredElementsHolder => d.declaredNames.headOption.getOrElse("")
          }
          owner.map(name => s"body of $name").getOrElse("containing block")
        case _ => ""
      }
      if (description != "") Some(description.parenthesize())
      else None
    }


    override def equals(obj: Any): Boolean = {
      obj match {
        case ref: AnyRef => this eq ref
        case _ => false
      }
    }
  }

  private def presentationText(srr: ScalaResolveResult, implicitResult: Option[ImplicitResult]): String = {
    def presentationPrefix: Option[String] = implicitResult.collect {
      case OkResult                        => "Applicable: "
      case DivergedImplicitResult          => "Diverged: "
      case CantInferTypeParameterResult    => "Can't infer type: "
      case ImplicitParameterNotFoundResult => "Not found: "
    }

    val name = srr.element.name

    if (srr.isImplicitParameterProblem) srr.implicitSearchState match {
      case Some(state) => s"Argument not found for type: ${typeText(state)}"
      case _           => s"Argument not found for $name"
    }
    else {
      val typeString = srr.implicitSearchState.collect {
        case state => s": ${typeText(state)}"
      }.getOrElse("")

      presentationPrefix.getOrElse("") + name + typeString
    }
  }

  private def typeText(state: ImplicitState): String = state.tp.presentableText(state.place)

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
      case rn: RootNode => rn.getChildren.toArray
      case _ => Array.empty
    }
  }

  override def createDescriptor(obj: Any, parent: NodeDescriptor[_]): NodeDescriptor[_] = {
    obj.asInstanceOf[NodeDescriptor[_]]
  }

  override def hasSomethingToCommit: Boolean = false

  override def commit(): Unit = {}
}
