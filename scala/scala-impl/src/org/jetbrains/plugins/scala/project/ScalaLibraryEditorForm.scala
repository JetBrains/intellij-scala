package org.jetbrains.plugins.scala.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.roots.libraries.ui
import com.intellij.openapi.roots.ui.distribution.DistributionComboBox
import com.intellij.openapi.ui.{ComboBox, ComponentValidator}
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.uiDesigner.core.{GridLayoutManager, Spacer}
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.ScalaLibraryEditorForm._
import org.jetbrains.plugins.scala.util.ui.distribution.{DistributionComboBoxUtils, GenericBundledDistributionInfo, LocalDistributionInfoWithShorterDisplayedPath, SimpleFileChooserInfo}

import java.awt._
import javax.swing._

class ScalaLibraryEditorForm(
  editorComponent: ui.LibraryEditorComponent[ScalaLibraryProperties]
) {
  final private val myClasspathEditor = new MyPathEditor(new FileChooserDescriptor(true, false, true, true, false, true))

  private val myClasspathPanel: JPanel = {
    val panel = new JPanel
    panel.setLayout(new BorderLayout(0, 0))
    panel.setBorder(IdeBorderFactory.createBorder)
    panel.add(myClasspathEditor.createComponent, BorderLayout.CENTER)
    panel
  }

  private val myLanguageLevel: ComboBox[ScalaLanguageLevel] = {
    val combo = new ComboBox[ScalaLanguageLevel]
    combo.setRenderer(new NonNullableValueBasedListRenderer[ScalaLanguageLevel](_.getVersion))
    combo.setModel(new DefaultComboBoxModel[ScalaLanguageLevel](publishedScalaLanguageLevels))
    combo
  }

  private val BundledCompilerBridgeDistributionInfo = new GenericBundledDistributionInfo

  private val myCompilerBridgeBinaryJarDistributionComboBox: DistributionComboBox = {
    val comboBox = new DistributionComboBox(editorComponent.getProject, new SimpleFileChooserInfo)
    comboBox.setSpecifyLocationActionName("Specify custom jar")
    comboBox.addDistributionIfNotExists(BundledCompilerBridgeDistributionInfo)

    DistributionComboBoxUtils.setCaretToStartOnContentChange(comboBox)
    DistributionComboBoxUtils.installLocalDistributionInfoPathTooltip(comboBox)

    comboBox
  }

  private val compilerBridgeValidator: Option[ComponentValidator] = editorComponent match {
    case parentDisposable: Disposable =>
      Some(DistributionComboBoxUtils.installLocalDistributionInfoPointsToExistingJarFileValidator(
        myCompilerBridgeBinaryJarDistributionComboBox,
        parentDisposable
      ))
    case _ =>
      None
  }

  val contentPanel: JPanel = {
    val panel = new JPanel(new GridLayoutManager(6, 3, JBUI.emptyInsets, -1, -1))

    import com.intellij.uiDesigner.core.{GridConstraints => GC}

    panel.add(new JBLabel(ScalaBundle.message("scala.library.editor.form.scala.version")), new GC(0, 0, 1, 1, GC.ANCHOR_WEST, GC.FILL_NONE, GC.SIZEPOLICY_FIXED, GC.SIZEPOLICY_FIXED, null, null, null, 1, false))
    panel.add(myLanguageLevel, new GC(0, 1, 1, 1, GC.ANCHOR_WEST, GC.FILL_NONE, GC.SIZEPOLICY_CAN_GROW, GC.SIZEPOLICY_FIXED, null, null, null, 0, false))

    panel.add(new JBLabel(ScalaBundle.message("scala.library.editor.form.compiler.bridge.jar")), new GC(1, 0, 1, 1, GC.ANCHOR_WEST, GC.FILL_NONE, GC.SIZEPOLICY_FIXED, GC.SIZEPOLICY_FIXED, null, null, null, 1, false))
    panel.add(myCompilerBridgeBinaryJarDistributionComboBox, new GC(
      1, 1, 1, 1, GC.ANCHOR_WEST, GC.FILL_NONE,
      GC.SIZEPOLICY_FIXED, GC.SIZEPOLICY_FIXED,
      null, null, new Dimension(300, 100), 0, false
    ))
    panel.add(new Spacer, new GC(1, 2, 1, 1, GC.ANCHOR_CENTER, GC.FILL_HORIZONTAL, GC.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false))

    panel.add(new Spacer, new GC(2, 0, 1, 3, GC.ANCHOR_CENTER, GC.FILL_VERTICAL, 1, GC.SIZEPOLICY_FIXED, new Dimension(-1, 5), new Dimension(-1, 5), new Dimension(-1, 5), 0, false))

    panel.add(new JBLabel(ScalaBundle.message("scala.library.editor.form.compiler.classpath")), new GC(3, 0, 1, 3, GC.ANCHOR_WEST, GC.FILL_NONE, GC.SIZEPOLICY_FIXED, GC.SIZEPOLICY_FIXED, new Dimension(-1, 20), null, null, 1, false))
    panel.add(myClasspathPanel, new GC(4, 0, 1, 3, GC.ANCHOR_CENTER, GC.FILL_BOTH, GC.SIZEPOLICY_CAN_SHRINK | GC.SIZEPOLICY_CAN_GROW, GC.SIZEPOLICY_CAN_SHRINK | GC.SIZEPOLICY_WANT_GROW, null, null, null, 0, false))

    //NOTE: after we split Scala SDK and Scala library, this label is not very informative because library jars
    // are mostly always empty (unless it's an IntelliJ project)
    panel.add(new JBLabel(ScalaBundle.message("scala.library.editor.form.standard.library")), new GC(5, 0, 1, 3, GC.ANCHOR_WEST, GC.FILL_NONE, GC.SIZEPOLICY_FIXED, GC.SIZEPOLICY_FIXED, new Dimension(-1, 20), null, null, 1, false))
    panel
  }

  def isValid: Boolean =
    compilerBridgeValidator.forall(_.getValidationInfo == null)

  def languageLevel: ScalaLanguageLevel = myLanguageLevel.getSelectedItem.asInstanceOf[ScalaLanguageLevel]
  def languageLevel_=(languageLevel: ScalaLanguageLevel): Unit = {
    // in case some new major release candidate version of the scala compiler is used
    // we want to display it's language level anyway (it's not added to the combobox when creating new SDK)
    val items = myLanguageLevel.items
    if (!items.contains(languageLevel)) {
      myLanguageLevel.setModel(new DefaultComboBoxModel[ScalaLanguageLevel]((languageLevel +: items).toArray))
    }
    myLanguageLevel.setSelectedItem(languageLevel)
  }

  def classpath: Array[String] = myClasspathEditor.getPaths
  def classpath_=(classpath: Array[String]): Unit = myClasspathEditor.setPaths(classpath)

  def compilerBridgeBinaryJar: Option[String] = {
    val info = myCompilerBridgeBinaryJarDistributionComboBox.getSelectedDistribution
    info match {
      case localDistributionInfo: LocalDistributionInfoWithShorterDisplayedPath =>
        val path = localDistributionInfo.canonicalPath
        val url = VfsUtilCore.pathToUrl(path)
        Some(url)
      case _ =>
        None //means "use bundled"
    }
  }

  def compilerBridgeBinaryJar_=(url: String): Unit = {
    val info = if (url == null)
      BundledCompilerBridgeDistributionInfo
    else {
      val path = MyPathEditor.pathToVirtualFile(url).getPath
      new LocalDistributionInfoWithShorterDisplayedPath(path)
    }
    myCompilerBridgeBinaryJarDistributionComboBox.setSelectedDistribution(info)
  }
}

private object ScalaLibraryEditorForm {
  private val publishedScalaLanguageLevels = ScalaLanguageLevel.publishedVersions.reverse

  implicit class ComboBoxOps[T](private val target: ComboBox[T]) extends AnyVal {
    def items: Seq[T] = {
      val model = target.getModel
      Seq.tabulate(model.getSize)(model.getElementAt)
    }
  }
}