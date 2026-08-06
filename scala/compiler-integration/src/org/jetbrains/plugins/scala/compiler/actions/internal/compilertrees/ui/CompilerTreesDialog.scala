package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.{GraphProperty, PropertyGraph}
import com.intellij.openapi.observable.util.BindUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{DialogWrapper, SimpleToolWindowPanel, ValidationInfo}
import com.intellij.openapi.util.Condition
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.{JBCheckBox, JBScrollPane}
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.{OnePixelSplitter, TreeUIHelper}
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.compiler.CompilerIntegrationBundle
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui.CompilerTreesDialog.escapeSpecialXmlTagsFromCompilerTreeTExt
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui.treeView.MyTree
import org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.{CompilerTreesCollectionListener, PhaseWithTreeText}
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ModuleExt

import java.awt.event.ActionEvent
import java.awt.{BorderLayout, Dimension}
import java.lang
import javax.swing._
import scala.util.matching.Regex
import scala.compiletime.uninitialized

final class CompilerTreesDialog(
  myProject: Project,
  myModule: Module,
  phasesCollectionProgress: ProgressIndicator
) extends DialogWrapper(myProject) {

  private var myEditor: EditorEx = uninitialized

  private object GraphProperties {
    private val graph: PropertyGraph = new PropertyGraph("internal.CompilerTreesDialog.propertyGraph", true)

    val showEmptyPhases: GraphProperty[java.lang.Boolean] = graph.property(TreeDisplayOptions.Default.showEmptyPhases)
    val showTasty: GraphProperty[java.lang.Boolean] = graph.property(TreeDisplayOptions.Default.showTasty)
    val showUncapturedMessages: GraphProperty[java.lang.Boolean] = graph.property(TreeDisplayOptions.Default.showUncapturedMessages)

    val lastSelectedPhase: GraphProperty[String] = graph.property(null)
    //the proportion is in percents (0 - 100) (can't use float because `BindUtil` doesn't have `BindUtil.bindFloatStorage` and only has `BindUtil.bindIntStorage`
    val proportionBetweenLeftAndRightPanels: GraphProperty[java.lang.Integer] = graph.property(30)

    def initBindings(): Unit = {
      // ATTENTION: we should bind the properties to persistent storage before they are bound to UI elements.
      // Otherwise, the saved values won't be effectively used in the UI.
      bindPropertiesToPersistentStorage()

      bindPropertiesAndUiElements()
    }

    private def bindPropertiesAndUiElements(): Unit = {
      //binding checkbox values to properties and tree updates
      bindCheckboxToProperty(UiComponents.myShowEmptyPhasesCb, GraphProperties.showEmptyPhases)
      bindCheckboxToProperty(UiComponents.myShowTastyCb, GraphProperties.showTasty)
      bindCheckboxToProperty(UiComponents.myShowUncapturedMessagesCb, GraphProperties.showUncapturedMessages)

      //binding "proportionBetweenLeftAndRightPanelsProperty" to splitter changes made by a user in UI
      UiComponents.mySplitter.addPropertyChangeListener(e => {
        if ("proportion" == e.getPropertyName) {
          val newProportion = e.getNewValue.asInstanceOf[lang.Float]
          GraphProperties.proportionBetweenLeftAndRightPanels.set((newProportion * 100).toInt)
        }
      })

      // Apply initial filter settings to the tree
      applyTreeDisplayOptionsToTree()
    }

    /** Bind a checkbox to a property: sync the initial state and update the tree on changes */
    private def bindCheckboxToProperty(checkbox: JBCheckBox, property: GraphProperty[java.lang.Boolean]): Unit = {
      checkbox.setSelected(property.get())
      checkbox.addActionListener((_: ActionEvent) => {
        property.set(checkbox.isSelected)

        // In addition to binding the values, also update the tree filters
        applyTreeDisplayOptionsToTree()
      })
    }
  }

  private var displayOptionsForTests: Option[TreeDisplayOptions] = None

  private def displayOptions: TreeDisplayOptions = {
    val optionsFromProperties = TreeDisplayOptions(
      GraphProperties.showEmptyPhases.get(),
      GraphProperties.showTasty.get(),
      GraphProperties.showUncapturedMessages.get()
    )
    displayOptionsForTests.getOrElse(optionsFromProperties)
  }

  @TestOnly
  def setDisplayOptionsForTests(displayOptions: TreeDisplayOptions): Unit = {
    displayOptionsForTests = Some(displayOptions)
    applyTreeDisplayOptionsToTree()
  }

  private object UiComponents {
    // Controls for display options, just above the tree view
    var myShowEmptyPhasesCb: JBCheckBox = uninitialized
    var myShowTastyCb: JBCheckBox = uninitialized
    var myShowUncapturedMessagesCb: JBCheckBox = uninitialized

    var myTree: MyTree = uninitialized
    var myTreeScrollPane: JBScrollPane = uninitialized

    /**
     * left part: tree view with phases<br>
     * right: part editor with compiler tree text corresponding to the selected phase
     */
    var mySplitter: OnePixelSplitter = uninitialized
  }

  private val collectedPhases: scala.collection.mutable.ArrayBuffer[PhaseWithTreeText] =
    scala.collection.mutable.ArrayBuffer.empty

  private class PhaseCollectorListener extends CompilerTreesCollectionListener {
    @volatile private var finished: Boolean = false

    def isFinished: Boolean = finished

    override def phaseAdded(phase: PhaseWithTreeText): Unit = {
      collectedPhases += phase
    }

    override def collectionFinished(): Unit = {
      finished = true
    }
  }

  private val phaseCollectorListener: PhaseCollectorListener = new PhaseCollectorListener()

  /**
   * Some async events from the background compilation can still be received after the dialog is closed
   * (even though the background process should be cancelled on dialog close)
   * We need to make sure that such events are not processed by the dialog.
   */
  private def invokeLaterIfDialogIsNotDisposed(action: => Unit): Unit = {
    ApplicationManager.getApplication.invokeLater(
      () => action,
      (_ => isDisposed): Condition[?]
    )
  }

  private val treeUpdateListener: CompilerTreesCollectionListener = new CompilerTreesCollectionListener {
    override def phaseAdded(phase: PhaseWithTreeText): Unit = {
      invokeLaterIfDialogIsNotDisposed {
        UiComponents.myTree.addPhase(phase)
      }
    }

    override def collectionFinished(): Unit = {
      invokeLaterIfDialogIsNotDisposed {
        UiComponents.myTree.setLoadingVisible(false)
      }
    }
  }

  private lazy val compilerTreesBroadcaster: CompilerTreesCollectionListener.Composite =
    new CompilerTreesCollectionListener.Composite(Seq(
      phaseCollectorListener,
      treeUpdateListener
    ))

  def compilerTreesListener: CompilerTreesCollectionListener.Composite = compilerTreesBroadcaster

  locally {
    // Make the dialog non-modal so it doesn't block and can receive updates
    setModal(false)

    init()

    GraphProperties.initBindings()
  }

  //Binding some dialog-related settings to storage in order the appearance of the dialog looks similar next time the action is invoked
  private def bindPropertiesToPersistentStorage(): Unit = {
    // Display/filter options
    BindUtil.bindBooleanStorage(GraphProperties.showEmptyPhases, "internal.CompilerTreesDialog.showEmptyPhases")
    BindUtil.bindBooleanStorage(GraphProperties.showTasty, "internal.CompilerTreesDialog.showTasty")
    BindUtil.bindBooleanStorage(GraphProperties.showUncapturedMessages, "internal.CompilerTreesDialog.showUncapturedMessages")

    // Last selection
    BindUtil.bindStorage(GraphProperties.lastSelectedPhase, "internal.CompilerTreesDialog.lastSelectedPhaseProperty")

    // Dialog UI proportions
    BindUtil.bindIntStorage(GraphProperties.proportionBetweenLeftAndRightPanels, "internal.CompilerTreesDialog.proportionBetweenLeftAndRightPanelsProperty")
  }

  /** Extract current display options from properties and update a tree */
  private def applyTreeDisplayOptionsToTree(): Unit = {
    UiComponents.myTree.updateDisplayOptions(displayOptions)
  }

  override protected def createCenterPanel: JComponent = {
    myEditor = createEditor("")
    UiComponents.myTree = UiDebugUtils.addDebugBorderIfEnabled(new MyTree(
      onPhaseSelected = updateEditorText,
      lastSelectedPhaseProperty = GraphProperties.lastSelectedPhase,
      initialDisplayOptions = displayOptions,
    ))
    TreeUIHelper.getInstance().installTreeSpeedSearch(UiComponents.myTree)

    UiComponents.myTree.setLoadingVisible(!phaseCollectorListener.isFinished)

    val toolWindowPanel = createToolWindowPanel(UiComponents.myTree)

    UiComponents.mySplitter = UiDebugUtils.addDebugBorderIfEnabled(new OnePixelSplitter(false, GraphProperties.proportionBetweenLeftAndRightPanels.get().toFloat / 100))
    UiComponents.mySplitter.setFirstComponent(toolWindowPanel)
    UiComponents.mySplitter.setSecondComponent(UiDebugUtils.addDebugBorderIfEnabled(myEditor.getComponent))

    val dialogPanel = UiDebugUtils.addDebugBorderIfEnabled(new JPanel(new BorderLayout()))
    dialogPanel.add(UiComponents.mySplitter, BorderLayout.CENTER)
    initPreferredSize(dialogPanel)
    dialogPanel
  }

  /**
   * Initializes the preferred size of the provided root panel
   * based on the main IDE frame's dimensions, scaling it to 80%.
   *
   * @param rootPanel the JPanel whose preferred size is to be set
   */
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
    import UiComponents._

    val toolWindowPanel = UiDebugUtils.addDebugBorderIfEnabled(new SimpleToolWindowPanel(true, true))

    myTreeScrollPane = UiDebugUtils.addDebugBorderIfEnabled(new JBScrollPane(tree))
    toolWindowPanel.setContent(myTreeScrollPane)

    val toolBar = UiDebugUtils.addDebugBorderIfEnabled(new JToolBar)
    toolBar.setFloatable(false)

    val label = UiDebugUtils.addDebugBorderIfEnabled(new javax.swing.JLabel(CompilerIntegrationBundle.message("show.label")))
    toolBar.add(label)

    // Add small spacing after label
    toolBar.addSeparator(new java.awt.Dimension(5, 0))

    // Add checkboxes in a row with proper spacing
    myShowEmptyPhasesCb = UiDebugUtils.addDebugBorderIfEnabled(new JBCheckBox(CompilerIntegrationBundle.message("show.empty.phases.short"), false))
    toolBar.add(myShowEmptyPhasesCb)

    toolBar.addSeparator(new java.awt.Dimension(5, 0))

    myShowTastyCb = UiDebugUtils.addDebugBorderIfEnabled(new JBCheckBox(CompilerIntegrationBundle.message("show.tasty"), false))
    toolBar.add(myShowTastyCb)

    toolBar.addSeparator(new java.awt.Dimension(5, 0))

    myShowUncapturedMessagesCb = UiDebugUtils.addDebugBorderIfEnabled(new JBCheckBox(CompilerIntegrationBundle.message("show.other.output"), false))
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
    val scalaFile = ScalaPsiElementFactory.createScalaFileFromText(documentText, scalaFeatures)(using myProject)
    val virtualFile = ScFile.VirtualFile.unapply(scalaFile).get

    val editor = EditorFactory.getInstance.createEditor(
      document,
      myProject,
      virtualFile,
      true
    ).asInstanceOf[EditorEx]
    editor.getSettings.setLineNumbersShown(true)
    UiDebugUtils.addDebugBorderIfEnabled(editor.getComponent)
    editor
  }

  @TestOnly
  def phasesFromModelForTests: Seq[PhaseWithTreeText] = {
    val tree = UiComponents.myTree
    if (tree == null) Seq.empty
    else tree.phasesFromModelForTests
  }

  @TestOnly
  def isCollectionFinishedForTests: Boolean = phaseCollectorListener.isFinished

  override protected def doValidate(): ValidationInfo = null //nothing to validate right now

  override protected def dispose(): Unit = {
    phasesCollectionProgress.cancel()
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
