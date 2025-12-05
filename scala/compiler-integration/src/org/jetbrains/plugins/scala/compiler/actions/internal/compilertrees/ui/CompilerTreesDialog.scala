package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.{GraphProperty, PropertyGraph}
import com.intellij.openapi.observable.util.BindUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{DialogWrapper, SimpleToolWindowPanel, ValidationInfo}
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.{JBCheckBox, JBScrollPane}
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.{OnePixelSplitter, TreeUIHelper}
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.compiler.CompilerIntegrationBundle
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.CompilerTrees
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.CompilerTrees.PhaseWithTreeText
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui.CompilerTreesDialog.escapeSpecialXmlTagsFromCompilerTreeTExt
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ModuleExt

import java.awt.event.ActionEvent
import java.awt.{BorderLayout, Dimension}
import java.lang
import javax.swing._
import scala.util.matching.Regex

final class CompilerTreesDialog(
  myProject: Project,
  myModule: Module,
  myCompilerTrees: CompilerTrees
) extends DialogWrapper(myProject) {

  private val propertyGraph: PropertyGraph = new PropertyGraph("internal.CompilerTreesDialog.propertyGraph", true)

  private val showEmptyPhasesProperty: GraphProperty[java.lang.Boolean] = propertyGraph.property(java.lang.Boolean.FALSE)
  private val showTastyProperty: GraphProperty[java.lang.Boolean] = propertyGraph.property(java.lang.Boolean.TRUE)
  private val showUncapturedMessagesProperty: GraphProperty[java.lang.Boolean] = propertyGraph.property(java.lang.Boolean.TRUE)

  private val lastSelectedPhaseProperty: GraphProperty[String] = propertyGraph.property(null)
  //proportion is in percents (0 - 100) (can't use float because `BindUtil` doesn't have `BindUtil.bindFloatStorage` and only has `BindUtil.bindIntStorage`
  private val proportionBetweenLeftAndRightPanelsProperty: GraphProperty[java.lang.Integer] = propertyGraph.property(30)

  //Binding some dialog-related settings to storage in order the appearance of the dialog looks similar next time the action is invoked
  BindUtil.bindBooleanStorage(showEmptyPhasesProperty, "internal.CompilerTreesDialog.showEmptyPhases")
  BindUtil.bindBooleanStorage(showTastyProperty, "internal.CompilerTreesDialog.showTasty")
  BindUtil.bindBooleanStorage(showUncapturedMessagesProperty, "internal.CompilerTreesDialog.showUncapturedMessages")
  BindUtil.bindStorage(lastSelectedPhaseProperty, "internal.CompilerTreesDialog.lastSelectedPhaseProperty")
  BindUtil.bindIntStorage(proportionBetweenLeftAndRightPanelsProperty, "internal.CompilerTreesDialog.proportionBetweenLeftAndRightPanelsProperty")

  private var myEditor: EditorEx = _
  private var myShowEmptyPhasesCb: JBCheckBox = _
  private var myShowTastyCb: JBCheckBox = _
  private var myShowUncapturedMessagesCb: JBCheckBox = _
  private var myTree: MyTree = _

  /**
   * left part: tree view with phases<br>
   * right: part editor with compiler tree text corresponding to the selected phase
   */
  private var mySplitter: OnePixelSplitter = _

  @TestOnly def getCompilerTrees: CompilerTrees = myCompilerTrees

  locally {
    init()
    bindPropertiesAndUiElements()
  }

  private def bindPropertiesAndUiElements(): Unit = {
    //binding checkboxes values to properties and tree updates
    bindCheckboxToProperty(myShowEmptyPhasesCb, showEmptyPhasesProperty)
    bindCheckboxToProperty(myShowTastyCb, showTastyProperty)
    bindCheckboxToProperty(myShowUncapturedMessagesCb, showUncapturedMessagesProperty)

    //binding "proportionBetweenLeftAndRightPanelsProperty" to splitter changes made by a user in UI
    mySplitter.addPropertyChangeListener(e => {
      if ("proportion" == e.getPropertyName) {
        val newProportion = e.getNewValue.asInstanceOf[lang.Float]
        proportionBetweenLeftAndRightPanelsProperty.set((newProportion * 100).toInt)
      }
    })

    // Apply initial filter settings to the tree
    updateTreeNodesFilters()
  }

  /** Bind a checkbox to a property: sync the initial state and update the tree on changes */
  private def bindCheckboxToProperty(checkbox: JBCheckBox, property: GraphProperty[java.lang.Boolean]): Unit = {
    checkbox.setSelected(property.get())
    checkbox.addActionListener((_: ActionEvent) => {
      property.set(checkbox.isSelected)

      // In addition to binding the values, also update the tree filters
      updateTreeNodesFilters()
    })
  }

  /** Extract current display options from properties and update tree */
  private def updateTreeNodesFilters(): Unit = {
    myTree.updateNodesVisibility(displayOptions)
  }

  private def displayOptions: TreeDisplayOptions = TreeDisplayOptions(
    showEmptyPhasesProperty.get(),
    showTastyProperty.get(),
    showUncapturedMessagesProperty.get()
  )

  override protected def createCenterPanel: JComponent = {
    myEditor = createEditor(myCompilerTrees.allPhasesTextConcatenated)
    myTree = new MyTree(
      myCompilerTrees,
      onPhaseSelected = updateEditorText,
      lastSelectedPhaseProperty
    )
    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree)

    val toolWindowPanel = createToolWindowPanel(myTree)

    mySplitter = new OnePixelSplitter(false, proportionBetweenLeftAndRightPanelsProperty.get().toFloat / 100)
    mySplitter.setFirstComponent(toolWindowPanel)
    mySplitter.setSecondComponent(myEditor.getComponent)

    val dialogPanel = new JPanel(new BorderLayout())
    dialogPanel.add(mySplitter, BorderLayout.CENTER)
    initPreferredSize(dialogPanel)
    dialogPanel
  }

  private def initPreferredSize(rootPanel: JPanel): Unit = {
    val ideFrame = WindowManager.getInstance().getIdeFrame(myProject)
    //Can be null in tests
    if (ideFrame == null)
      return

    val ideFrameSize = ideFrame.getComponent.getSize()
    val ratio = 0.8
    val dimension = new Dimension(
      (ideFrameSize.width * ratio).asInstanceOf[Int],
      (ideFrameSize.height * ratio).asInstanceOf[Int]
    )
    rootPanel.setPreferredSize(dimension)
  }

  private def updateEditorText(phaseWithText: PhaseWithTreeText): Unit = {
    inWriteAction {
      val text = phaseWithText.phaseText
      val documentText = escapeSpecialXmlTagsFromCompilerTreeTExt(text)
      val document = myEditor.getDocument
      document.setText(documentText)
    }
  }

  // Schematic example ofo the panel:
  // Show: [x] Show empty phases [x] Show Tasty [ ] Show uncaptured messages
  private def createToolWindowPanel(tree: Tree): SimpleToolWindowPanel = {
    val toolWindowPanel = new SimpleToolWindowPanel(true, true)

    toolWindowPanel.setContent(new JBScrollPane(tree))

    val toolBar = new JToolBar
    toolBar.setFloatable(false)
    
    val label = new javax.swing.JLabel(CompilerIntegrationBundle.message("show.label"))
    toolBar.add(label)

    // Add small spacing after label
    toolBar.addSeparator(new java.awt.Dimension(5, 0))

    // Add checkboxes in a row with proper spacing
    myShowEmptyPhasesCb = new JBCheckBox(CompilerIntegrationBundle.message("show.empty.phases.short"), false)
    toolBar.add(myShowEmptyPhasesCb)

    toolBar.addSeparator(new java.awt.Dimension(5, 0))

    myShowTastyCb = new JBCheckBox(CompilerIntegrationBundle.message("show.tasty"), false)
    toolBar.add(myShowTastyCb)

    toolBar.addSeparator(new java.awt.Dimension(5, 0))

    myShowUncapturedMessagesCb = new JBCheckBox(CompilerIntegrationBundle.message("show.other.output"), false)
    toolBar.add(myShowUncapturedMessagesCb)

    toolWindowPanel.setToolbar(toolBar)
    toolWindowPanel
  }

  //NOTE: we apply Scala language highlighting to the file.
  //Even though compiler trees might be not 100% correct Scala syntax
  //it's still useful to highlight scala syntax wherever it's possible
  private def createEditor(documentText: String): EditorEx = {
    val document = EditorFactory.getInstance.createDocument(documentText)

    val scalaFeatures = myModule.features
    val scalaFile = ScalaPsiElementFactory.createScalaFileFromText(documentText, scalaFeatures)(myProject)
    val virtualFile = ScFile.VirtualFile.unapply(scalaFile).get

    val editor = EditorFactory.getInstance.createEditor(
      document,
      myProject,
      virtualFile,
      true
    ).asInstanceOf[EditorEx]
    editor.getSettings.setLineNumbersShown(true)
    editor
  }

  override protected def doValidate(): ValidationInfo = null //nothing to validate right now

  override protected def dispose(): Unit = {
    EditorFactory.getInstance.releaseEditor(myEditor)
    super.dispose()
  }
}

object CompilerTreesDialog {
  private val KnownSpecialHtmlTags = Seq(
    "<accessor>",
    "<artifact>",
    "<bridge>",
    "<empty>",
    "<caseaccessor>",
    "<paramaccessor>",
    "<stable>",
    "<static>",
    "<synthetic>",
  )
  private val KnownSpecialHtmlTagWithPrecedingSpaceWithRegep: Seq[(String, Regex)] =
    KnownSpecialHtmlTags.map(tag => (tag, s" $tag".r))

  /**
   * The method makes the scala compiler tree look closer to Scala code.
   *
   * Scala compiler adds special XML tags for some constructs which is not valid Scala code.
   * We still want to parse as much as possible and highlight scala tokens - it makes the tree more readable
   * So we replace those invalids constructs with our alternatives.
   *
   * Examples from compiler trees: {{{
   *   package <empty>
   *   def <init>(): MyClass = ...
   *   implicit <stable> <accessor> def s(): String = MyClass.this.s;
   *   lazy <artifact> val C1$module: scala.runtime.LazyRef = new scala.runtime.LazyRef();
   *   <synthetic> def productArity(): Int = 0;
   *   <synthetic> <paramaccessor> <artifact> private[this] val $outer: MyClass = _;
   *   case <synthetic> <bridge> <artifact> def apply(): Object = MyClass$C1$2.this.apply();
   * }}}
   */
  private def escapeSpecialXmlTagsFromCompilerTreeTExt(text: String): String = {
    val textWithReplacedSpecialIdentifiers = text
      .replace("package <empty>", "package `<empty>`")
      .replace("def <init>", "def `<init>`")
    val textWithReplacedOtherTags =
      KnownSpecialHtmlTagWithPrecedingSpaceWithRegep.foldLeft(textWithReplacedSpecialIdentifiers) { case (t, (tag, tagRegexp)) =>
        tagRegexp.replaceAllIn(t, s""" /*$tag*/""")
      }
    textWithReplacedOtherTags
  }
}