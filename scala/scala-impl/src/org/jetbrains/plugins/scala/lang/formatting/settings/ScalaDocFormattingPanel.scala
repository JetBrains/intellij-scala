package org.jetbrains.plugins.scala.lang.formatting.settings

import java.awt.BorderLayout
import java.awt.event.{ActionEvent, ActionListener}
import javax.swing.{JCheckBox, JComponent, JPanel}

import com.intellij.application.options.codeStyle.OptionTreeWithPreviewPanel
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.psi.codeStyle.{CodeStyleSettings, LanguageCodeStyleSettingsProvider}
import com.intellij.ui.border.CustomLineBorder
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}

/**
  * @author Roman.Shein
  *         Date: 13.11.2015
  */
class ScalaDocFormattingPanel(val settings: CodeStyleSettings) extends OptionTreeWithPreviewPanel(settings) {

  private var myEnableCheckBox: JCheckBox = null
  private var myAsteriskStyleCheckBox: JCheckBox = null
  private val myScaladocPanel: JPanel = new JPanel(new BorderLayout)

  init()

  override def init(): Unit = {
    super.init()

    myEnableCheckBox = new JCheckBox("Enable scaladoc formatting")
    myEnableCheckBox.addActionListener(new ActionListener() {
      override def actionPerformed(e: ActionEvent): Unit = update()
    })

    myAsteriskStyleCheckBox = new JCheckBox("Use scaladoc indent for leading asterisk")

    myPanel.setBorder(new CustomLineBorder(OnePixelDivider.BACKGROUND, 1, 0, 0, 0))
    myScaladocPanel.add(BorderLayout.CENTER, myPanel)
    val topPanel = new JPanel(new BorderLayout)
    myScaladocPanel.add(topPanel, BorderLayout.NORTH)
    topPanel.add(myEnableCheckBox, BorderLayout.NORTH)
    topPanel.add(myAsteriskStyleCheckBox, BorderLayout.SOUTH)
  }

  override def getSettingsType = LanguageCodeStyleSettingsProvider.SettingsType.LANGUAGE_SPECIFIC

  override def getPanel: JPanel = myScaladocPanel

  protected override def initTables(): Unit = {
    initCustomOptions(ScalaDocFormattingPanel.ALIGNMENT_GROUP)
    initCustomOptions(ScalaDocFormattingPanel.BLANK_LINES_GROUP)
    initCustomOptions(ScalaDocFormattingPanel.OTHER_GROUP)
  }

  protected override def getRightMargin: Int = 47

  protected override def getPreviewText: String =
    """
      |/**
      |  * Foos the given x, returning foo'ed x.
      |  * @note Note that this tag is here just to show
      |  * how exactly alignment for tags different from parameters and return tags
      |  * @forExample   Even if the tag is not valid, formatting will still be fine
      |  *   also, if you choose to preserver spaces in tags, no spaces will be removed after tag value
      |  * @param x Some parameter named x that has
      |  * a multiline description
      |  * @param yy Another parameter named yy
      |  * @param longParamName Another parameter with a long name
      |  * @return Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
      |  *         eiusmod tempor incididunt ut labore et dolore magna aliqua.
      |  * @throws RuntimeException whenever it feels like it
      |  */
      |def foo(x: Int, yy: Iny, longParamName: Int): Int
    """.stripMargin.replace("\r", "")

  private def update() {
    setEnabled(getPanel, myEnableCheckBox.isSelected)
    myEnableCheckBox.setEnabled(true)
    myAsteriskStyleCheckBox.setEnabled(true)
  }

  private def setEnabled(c: JComponent, enabled: Boolean) {
    c.setEnabled(enabled)
    val children = c.getComponents
    for (child <- children) {
      child match {
        case c1: JComponent =>
          setEnabled(c1, enabled)
        case _ =>
      }
    }
  }

  override def apply(settings: CodeStyleSettings) {
    super.apply(settings)
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaSettings.ENABLE_SCALADOC_FORMATTING = myEnableCheckBox.isSelected
    scalaSettings.USE_SCALADOC2_FORMATTING = myAsteriskStyleCheckBox.isSelected
  }

  protected override def resetImpl(settings: CodeStyleSettings) {
    super.resetImpl(settings)
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    myEnableCheckBox.setSelected(scalaSettings.ENABLE_SCALADOC_FORMATTING)
    myAsteriskStyleCheckBox.setSelected(scalaSettings.USE_SCALADOC2_FORMATTING)
    update()
  }

  override def isModified(settings: CodeStyleSettings): Boolean = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    super.isModified(settings) || myEnableCheckBox.isSelected != scalaSettings.ENABLE_SCALADOC_FORMATTING ||
      myAsteriskStyleCheckBox.isSelected != scalaSettings.USE_SCALADOC2_FORMATTING
  }

  protected override def getFileType: FileType = ScalaFileType.INSTANCE

  protected override def customizeSettings() {
    val provider: LanguageCodeStyleSettingsProvider = LanguageCodeStyleSettingsProvider.forLanguage(ScalaLanguage.INSTANCE)
    if (provider != null) {
      provider.customizeSettings(this, getSettingsType)
    }
  }

  protected override def getTabTitle: String = "ScalaDoc"
}

object ScalaDocFormattingPanel {
  val BLANK_LINES_GROUP = "Blank lines"
  val ALIGNMENT_GROUP = "Alignment"
  val OTHER_GROUP = "Other"
}