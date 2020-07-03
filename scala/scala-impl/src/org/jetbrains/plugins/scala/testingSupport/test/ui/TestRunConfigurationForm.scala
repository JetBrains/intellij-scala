package org.jetbrains.plugins.scala.testingSupport.test.ui

import java.awt._
import java.util

import com.intellij.application.options.ModuleDescriptionsComboBox
import com.intellij.execution.{ExecutionBundle, ShortenCommandLine}
import com.intellij.execution.configuration.BrowseModuleValueActionListener
import com.intellij.execution.ui.{ClassBrowser, ConfigurationModuleSelector, DefaultJreSelector, JrePathEditor, ShortenCommandLineModeCombo}
import com.intellij.ide.util.{ClassFilter, PackageChooserDialog}
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{ComboBox, ComponentWithBrowseButton, LabeledComponent}
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.{EditorTextField, EditorTextFieldWithBrowseButton, EnumComboBoxModel, IdeBorderFactory}
import com.intellij.uiDesigner.core.{GridConstraints, Spacer}
import com.intellij.util.ui.{JBUI, UIUtil}
import javax.swing._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.IteratorExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.settings.SimpleMappingListCellRenderer
import org.jetbrains.plugins.scala.testingSupport.test._
import org.jetbrains.plugins.scala.testingSupport.test.testdata._
import org.jetbrains.plugins.scala.testingSupport.test.ui.TestRunConfigurationForm.TextWithMnemonic.Mnemonic
import org.jetbrains.plugins.scala.testingSupport.test.ui.TestRunConfigurationForm.{PackageChooserActionListener, _}
import org.jetbrains.sbt.settings.SbtSettings

import scala.collection.JavaConverters.asScalaBufferConverter

//noinspection ConvertNullInitializerToUnderscore
final class TestRunConfigurationForm(val myProject: Project) {

  private var myWholePanel: JPanel                      = null
  def getPanel: JPanel = myWholePanel

  private var mySuitePaths    : Seq[String]                 = null
  private var myModuleSelector: ConfigurationModuleSelector = null

  // TOP PANEL: TestKind-specific
  private var myTestKind      : LabeledComponent[ComboBox[TestKind]]                                  = null
  private var myClass         : LabeledComponent[EditorTextFieldWithBrowseButton]                     = null
  private var myTestName      : LabeledComponent[TestRunConfigurationForm.MyMultilineEditorTextField] = null
  private var myPackage       : LabeledComponent[EditorTextFieldWithBrowseButton]                     = null
  private var mySearchForTest : LabeledComponent[JComboBox[SearchForTest]]                            = null
  private var myRegex         : LabeledComponent[RegexpPanel]                                         = null
  private var myUseSbtCheckBox: JCheckBox                                                             = null
  private var myUseUiWithSbt  : JCheckBox                                                             = null

  // BOTTOM PANEL: common options
  private var myCommonScalaParameters       : CommonScalaParametersPanel                    = null
  private var myModule                      : LabeledComponent[ModuleDescriptionsComboBox]  = null
  private var myJrePathEditor               : JrePathEditor                                 = null
  private var myShortenClasspathMode        : LabeledComponent[ShortenCommandLineModeCombo] = null
  private var myShowProgressMessagesCheckBox: JCheckBox                                     = null

  init()

  def init(): Unit = {
    createUiComponents()

    myTestKind.component.setModel(new EnumComboBoxModel[TestKind](classOf[TestKind]))
    myTestKind.component.setRenderer(SimpleMappingListCellRenderer.create(
      TestKind.ALL_IN_PACKAGE -> ScalaBundle.message("test.run.config.test.kind.all.in.package"),
      TestKind.CLAZZ -> ScalaBundle.message("test.run.config.test.kind.class"),
      TestKind.TEST_NAME -> ScalaBundle.message("test.run.config.test.kind.test.name"),
      TestKind.REGEXP -> ScalaBundle.message("test.run.config.test.kind.regular.expression")
    ))

    mySearchForTest.component.setModel(new EnumComboBoxModel[SearchForTest](classOf[SearchForTest]))
    mySearchForTest.component.setRenderer(SimpleMappingListCellRenderer.create(
      SearchForTest.IN_WHOLE_PROJECT -> ScalaBundle.message("test.run.config.search.scope.in.whole.project"),
      SearchForTest.IN_SINGLE_MODULE -> ScalaBundle.message("test.run.config.search.scope.in.single.module"),
      SearchForTest.ACCROSS_MODULE_DEPENDENCIES -> ScalaBundle.message("test.run.config.search.scope.across.module.dependencies")
    ))

    myClass.getComponent.setBrowser(new MyClassBrowser(myProject))
    myPackage.getComponent.setBrowser(new PackageChooserActionListener(myProject))

    myJrePathEditor.setDefaultJreSelector(DefaultJreSelector.fromModuleDependencies(myModule.getComponent, false))
    myModuleSelector = new ConfigurationModuleSelector(myProject, myModule.getComponent)

    myTestKind.component.addItemListener(_ => UiComponentsVisibility.update())
    mySearchForTest.component.addItemListener(_ => UiComponentsVisibility.update())
    myUseSbtCheckBox.addItemListener(_ => UiComponentsVisibility.update())

    UiComponentsVisibility.update()
  }

  private def isModuleSelectionSupported(testKind: TestKind, searchScope: SearchForTest): Boolean =
    (testKind, searchScope) match {
      case (TestKind.ALL_IN_PACKAGE, SearchForTest.IN_WHOLE_PROJECT) => false
      case _                                                         => true
    }

  def resetFrom(configuration: AbstractTestRunConfiguration): Unit = {
    val configurationData = configuration.testConfigurationData
    configurationData match {
      case data: AllInPackageTestData => setTestPackagePath(data.testPackagePath)
      case data: RegexpTestData       => setRegexps(data.regexps)
      case data: ClassTestData        =>
        data match {
          case singleTest: SingleTestData =>  setTestName(singleTest.getTestName)
          case _=>
        }
        setTestClassPath(data.testClassPath)
      case _ =>
    }

    setTestKind(configurationData.getKind)
    setSearchForTest(configurationData.getSearchTest)

    resetSbtOptionsFrom(configuration)

    myCommonScalaParameters.reset(configurationData)
    myModuleSelector.reset(configuration)
    myJrePathEditor.setPathOrName(configurationData.getJrePath, true)
    setShortenCommandLine(configuration.getShortenCommandLine)
    setShowProgressMessages(configurationData.getShowProgressMessages)

    mySuitePaths = configuration.javaSuitePaths.asScala

    UiComponentsVisibility.update(configuration)
  }

  def applyTo(configuration: AbstractTestRunConfiguration): Unit = {
    configuration.setTestKind(this.getTestKind)
    configuration.setModule(this.getModule)
    configuration.testConfigurationData = TestConfigurationData.createFromForm(this, configuration)
    configuration.testConfigurationData.initWorkingDirIfEmpty()
    configuration.setShortenCommandLine(getShortenCommandLine)
  }

  private def resetSbtOptionsFrom(configuration: AbstractTestRunConfiguration): Unit = {
    val configurationData = configuration.testConfigurationData
    setUseSbt(configurationData.useSbt)
    setUseUiWithSbt(configurationData.useUiWithSbt)
  }

  def getModule: Module = myModuleSelector.getModule
  def getJrePath: String = myJrePathEditor.getJrePathOrName

  def getTestKind: TestKind = myTestKind.getComponent.getSelectedItem.asInstanceOf[TestKind]
  def getTestClassPath: String = myClass.getComponent.getText
  def getTestName: String = myTestName.getComponent.getText
  def getTestPackagePath: String = myPackage.getComponent.getText
  def getSearchForTest: SearchForTest = mySearchForTest.getComponent.getSelectedItem.asInstanceOf[SearchForTest]
  def getRegexps: (Array[String], Array[String]) = myRegex.getComponent.getRegexps
  def getUseSbt: Boolean = myUseSbtCheckBox.isSelected
  def getUseUiWithSbt: Boolean = myUseUiWithSbt.isSelected
  def getJavaOptions: String = myCommonScalaParameters.getVMParameters
  def getEnvironmentVariables: util.Map[String, String] = myCommonScalaParameters.getEnvironmentVariables
  def getWorkingDirectory: String = myCommonScalaParameters.getWorkingDirectoryAccessor.getText
  def getTestArgs: String = myCommonScalaParameters.getProgramParameters
  def getShowProgressMessages: Boolean = myShowProgressMessagesCheckBox.isSelected
  def getShortenCommandLine: ShortenCommandLine = myShortenClasspathMode.getComponent.getSelectedItem

  private def setTestKind(kind: TestKind): Unit = myTestKind.getComponent.setSelectedItem(kind)
  private def setTestClassPath(s: String): Unit = myClass.getComponent.setText(s)
  private def setTestName(s: String): Unit = myTestName.getComponent.setText(s)
  private def setTestPackagePath(s: String): Unit = myPackage.getComponent.setText(s)
  private def setSearchForTest(searchTest: SearchForTest): Unit = mySearchForTest.getComponent.setSelectedItem(searchTest)
  private def setRegexps(classRegexps: Array[String], testRegexps: Array[String]): Unit = myRegex.getComponent.setRegexps(classRegexps, testRegexps)
  private def setRegexps(regexps: (Array[String], Array[String])): Unit = setRegexps(regexps._1, regexps._2)
  private def setUseSbt(b: Boolean): Unit = myUseSbtCheckBox.setSelected(b)
  private def setUseUiWithSbt(b: Boolean): Unit = myUseUiWithSbt.setSelected(b)
  private def setShowProgressMessages(b: Boolean): Unit = myShowProgressMessagesCheckBox.setSelected(b)
  private def setShortenCommandLine(mode: ShortenCommandLine): Unit = myShortenClasspathMode.getComponent.setSelectedItem(mode)

  private object UiComponentsVisibility {

    def update(): Unit = {
      val testKind = getTestKind
      hideAll()
      testKind match {
        case TestKind.CLAZZ          => setClassVisible(true)
        case TestKind.TEST_NAME      => setTestNameVisible(true)
        case TestKind.REGEXP         => setRegexpVisible(true)
        case TestKind.ALL_IN_PACKAGE => setPackageVisible(true)
      }

      val packageSearchScope = getSearchForTest
      myModule.setEnabled(isModuleSelectionSupported(testKind, packageSearchScope))
      myUseUiWithSbt.setEnabled(myUseSbtCheckBox.isSelected)
    }

    def update(configuration: AbstractTestRunConfiguration): Unit = {
      update()
      val projectHasSbt = hasSbt(myProject)
      myUseSbtCheckBox.setVisible(projectHasSbt) // TODO: hide if sbt support is null/None
      myUseUiWithSbt.setVisible(projectHasSbt && configuration.sbtSupport.allowsSbtUiRun)
    }

    private def hideAll(): Unit = {
      setPackageVisible(false)
      setClassVisible(false)
      setTestNameVisible(false)
      setRegexpVisible(false)
    }

    private def setClassVisible(visible: Boolean): Unit =
      myClass.setVisible(visible)

    private def setTestNameVisible(visible: Boolean): Unit = {
      myTestName.setVisible(visible)
      myClass.setVisible(visible)
    }

    private def setPackageVisible(visible: Boolean): Unit = {
      myPackage.setVisible(visible)
      mySearchForTest.setVisible(visible)
    }

    private def setRegexpVisible(visible: Boolean): Unit =
      myRegex.setVisible(visible)
  }

  private def createUiComponents(): Unit = {
    myWholePanel = new JPanel

    implicit val uiBuilder: GridUiBuilder = new GridUiBuilder(myWholePanel, columns = 4)
    import uiBuilder._

    myTestKind = labeledComponent(ScalaBundle.message("test.run.config.test.kind"), new ComboBox[TestKind])

    myUseSbtCheckBox = (new JCheckBox).setTextWithMnemonic(ScalaBundle.message("test.run.config.use.sbt"))
    myUseUiWithSbt = (new JCheckBox).setTextWithMnemonic(ScalaBundle.message("test.run.config.use.ui.with.sbt"))

    appendRow(myTestKind, myUseSbtCheckBox, myUseUiWithSbt, new Spacer())

    myClass = append(labeledComponent(
      ScalaBundle.message("test.run.config.test.class"),
      new EditorTextFieldWithBrowseButton(myProject, true)
    ))
    myTestName = append(labeledComponent(
      ScalaBundle.message("test.run.config.test.name"),
      new TestRunConfigurationForm.MyMultilineEditorTextField, isMultilineContent = true
    ))
    myPackage = append(labeledComponent(
      ScalaBundle.message("test.run.config.test.package"),
      new EditorTextFieldWithBrowseButton(myProject, false)
    ))
    mySearchForTest = labeledComponent(
      ScalaBundle.message("test.run.config.search.for.tests"),
      new ComboBox[SearchForTest]
    )
    appendRow(mySearchForTest, new Spacer(), new Spacer())
    //    append(mySearchForTest)
    myRegex = append(labeledComponent(
      ScalaBundle.message("test.run.config.regular.expressions"),
      new RegexpPanel, isMultilineContent = true
    ))

    addSeparatorBetweenTopAndBottomPanel()

    myCommonScalaParameters = append(new CommonScalaParametersPanel)
    myModule = append(labeledComponent(
      ExecutionBundle.message("application.configuration.use.classpath.and.jdk.of.module.label"),
      new ModuleDescriptionsComboBox
    ))
    myJrePathEditor = append(new JrePathEditor)
    myShortenClasspathMode = append(labeledComponent(
      ExecutionBundle.message("application.configuration.shorten.command.line.label"),
      new ShortenCommandLineModeCombo(myProject, myJrePathEditor, myModule.getComponent())
    ))
    myShowProgressMessagesCheckBox = (new JCheckBox)
      .setTextWithMnemonic(ScalaBundle.message("test.run.config.print.information.messages.to.console"))
    append(myShowProgressMessagesCheckBox)

    addBottomFiller()

    val topPanelAnchor = myRegex.getLabel
    mySearchForTest.setAnchor(topPanelAnchor)
    myTestKind.setAnchor(topPanelAnchor)
    myClass.setAnchor(topPanelAnchor)
    myTestName.setAnchor(topPanelAnchor)
    myPackage.setAnchor(topPanelAnchor)

    myJrePathEditor.setAnchor(myModule.getLabel)
    myCommonScalaParameters.setAnchor(myModule.getLabel)
    myShortenClasspathMode.setAnchor(myModule.getLabel)
  }

  private def labeledComponent[T <: JComponent](@Nls labelText: String, component: T): LabeledComponent[T] =
    labeledComponent(labelText, component, isMultilineContent = false)

  private def labeledComponent[T <: JComponent](@Nls labelText: String, component: T, isMultilineContent: Boolean): LabeledComponent[T] = {
    val result = new LabeledComponent[T]
    result.setText(labelText)
    result.setComponent(component)
    result.setLabelLocation(BorderLayout.WEST)
    if (isMultilineContent) {
      // by default LabeledComponent's label is aligned in center, so if the inner component is multiline
      // the label has a gab in the TOP and BOTTOM. Moreover, when the inner component grows in height
      // the label "jumps" below it's previous position, which looks awkward
      // This is a workaround to make label position fixed in the TOP
      val TOP_PADDING = 5
      val label = result.getLabel
      label.setVerticalAlignment(SwingConstants.TOP)
      label.setBorder(IdeBorderFactory.createEmptyBorder(JBUI.insetsTop(TOP_PADDING)))
    }
    result
  }

  private def addSeparatorBetweenTopAndBottomPanel()(implicit uiBuilder: GridUiBuilder) = {
    val dim = new Dimension(-1, 32)
    import uiBuilder._
    append(new Spacer, new GridConstraints(
      rowIdx, 0, 1, columns,
      GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
      dim, dim, dim, 0, false
    ))
    append(new JSeparator(SwingConstants.HORIZONTAL))
  }

  private def addBottomFiller()(implicit uiBuilder: GridUiBuilder): Unit = {
    import uiBuilder._
    append(new Spacer, new GridConstraints(
      rowIdx, 0, 1, columns,
      GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL,
      GridConstraints.SIZEPOLICY_WANT_GROW | GridConstraints.SIZEPOLICY_CAN_GROW,
      GridConstraints.SIZEPOLICY_WANT_GROW | GridConstraints.SIZEPOLICY_CAN_GROW,
      null, null, null, 0, false
    ))
  }

  private class MyClassBrowser[T <: JComponent](project: Project)
    extends ClassBrowser[T](project, ScalaBundle.message("test.run.config.choose.test.class")) {

    override protected def getFilter: ClassFilter.ClassFilterWithScope = new ClassFilter.ClassFilterWithScope() {
      override def getScope: GlobalSearchScope =
        getModule match {
          case null   => GlobalSearchScope.allScope(project)
          case module => GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
        }

      override def isAccepted(aClass: PsiClass): Boolean = {
        if (!getScope.accept(aClass.getContainingFile.getVirtualFile)) return false
        val baseSuite = findBaseSuiteClass(project, aClass)
        baseSuite.isDefined
      }

      private def findBaseSuiteClass(project: Project, aClass: PsiClass): Option[PsiClass] =
        (for {
          suitePath  <- mySuitePaths.iterator
          suiteClass <- ScalaPsiManager.instance(project).getCachedClasses(getScope, suitePath).iterator
          if ScalaPsiUtil.isInheritorDeep(aClass, suiteClass)
        } yield suiteClass).headOption
    }

    override protected def findClass(className: String): PsiClass = {
      val cachedClass = ScalaPsiManager.instance(project).getCachedClass(GlobalSearchScope.allScope(project), className)
      if (cachedClass.isEmpty) null
      else cachedClass.get
    }
  }
}

private object TestRunConfigurationForm {

  /** copied from [[com.intellij.execution.junit2.configuration.JUnitConfigurable]] */
  private class PackageChooserActionListener[T <: JComponent](val project: Project)
    extends BrowseModuleValueActionListener[T](project) {

    override protected def showDialog: String = {
      val dialog = new PackageChooserDialog(ExecutionBundle.message("choose.package.dialog.title"), getProject)
      dialog.show()
      val aPackage = dialog.getSelectedPackage
      if (aPackage != null) aPackage.getQualifiedName
      else null
    }
  }

  private class MyMultilineEditorTextField() extends EditorTextField {
    this.setOneLineMode(false)
    override protected def updateBorder(editor: EditorEx): Unit = setupBorder(editor) // in base class border isn't set in multiline mode
  }

  /** based on [[LabeledComponent.TextWithMnemonic]] */
  case class TextWithMnemonic(text: String, mnemonic: Option[Mnemonic]) {
    def setTo(button: AbstractButton): Unit = {
      button.setText(text)
      mnemonic match {
        case Some(Mnemonic(char, index)) =>
          button.setMnemonic(char)
          button.setDisplayedMnemonicIndex(index)
        case _ =>
      }
    }
  }

  object TextWithMnemonic {
    case class Mnemonic(char: Char, index: Int)

    def apply(text: String): TextWithMnemonic = {
      val idx = UIUtil.getDisplayMnemonicIndex(text)
      if (idx != -1)
        new TextWithMnemonic(new StringBuilder(text).deleteCharAt(idx).toString, Some(Mnemonic(text.charAt(idx), idx)))
      else
        new TextWithMnemonic(text, None)
    }
  }

  implicit class AbstractButtonOps[T <: AbstractButton](private val button: T) extends AnyVal {
    def setTextWithMnemonic(text: String): T = {
      TextWithMnemonic(text).setTo(button)
      button
    }
  }
  implicit class EditorTextFieldWithBrowseButtonOps[C <: JComponent](private val target: EditorTextFieldWithBrowseButton) extends AnyVal {
    def setBrowser[B](browser: B)(implicit ev: B <:< BrowseModuleValueActionListener[C]): Unit =
      browser.setField(target.asInstanceOf[ComponentWithBrowseButton[C]])
  }
  implicit class LabeledComponentOps[C <: JComponent](private val target: LabeledComponent[C]) extends AnyVal {
    def component: C = target.getComponent
  }

  private def hasSbt(project: Project): Boolean = {
    val sbtSettings = SbtSettings.getInstance(project)
    val sbtSettingsEmpty = sbtSettings == null || sbtSettings.getLinkedProjectsSettings.isEmpty
    !sbtSettingsEmpty
  }
}
