package org.jetbrains.plugins.scala.util.ui.distribution

import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.ui.distribution.{DistributionComboBox, DistributionInfo, LocalDistributionInfo}
import com.intellij.openapi.ui.{ComponentValidator, ValidationInfo}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaBundle

import java.awt.event.ItemEvent
import java.io.File
import javax.swing.JTextField
import javax.swing.text.{AbstractDocument, AttributeSet, BadLocationException, DocumentFilter}

private[scala]
object DistributionComboBoxUtils {

  def installLocalDistributionInfoPointsToExistingJarFileValidator(
    comboBox: DistributionComboBox,
    parentDisposable: Disposable
  ): ComponentValidator = {
    val validator = new ComponentValidator(parentDisposable)
    validator.enableValidation()
    validator.installOn(comboBox)

    //NOTE: com.intellij.openapi.ui.ComponentValidator.withValidator method doesn't work for some reason
    // so we use custom listener for our purposes
    comboBox.addItemListener((itemEvent: ItemEvent) => {
      val validationInfo = if (isSelectedPathAnExistingJarFile(itemEvent))
        null
      else
        new ValidationInfo(ScalaBundle.message("selected.file.is.not.a.valid.jar.file")).forComponent(comboBox)
      validator.updateInfo(validationInfo)
    })

    validator
  }

  private def isSelectedPathAnExistingJarFile(itemEvent: ItemEvent): Boolean =
    itemEvent.getItem match {
      case distribution: DistributionComboBox.Item.Distribution =>
        isSelectedPathAnExistingJarFile(distribution.getInfo)
      case _ =>
        true
    }

  private def isSelectedPathAnExistingJarFile(distributionInfo: DistributionInfo): Boolean = {
    val path = getLocalDistributionInfoPath(distributionInfo)
    path == null || path.nonEmpty && {
      val file = new File(path)
      file.isFile && file.getName.endsWith(".jar")
    }
  }

  def installLocalDistributionInfoPathTooltip(comboBox: DistributionComboBox): Unit = {
    comboBox.addItemListener((_: ItemEvent) => {
      val path = DistributionComboBoxUtils.getLocalDistributionInfoPath(comboBox)
      comboBox.setToolTipText(path)
    })
  }

  @Nullable
  def getLocalDistributionInfoPath(comboBox: DistributionComboBox): String =
    getLocalDistributionInfoPath(comboBox.getSelectedDistribution)

  private def getLocalDistributionInfoPath(distribution: DistributionInfo): String =
    distribution match {
      case info: LocalDistributionInfo => info.getPath
      case info: LocalDistributionInfoWithShorterDisplayedPath => info.canonicalPath
      case _ => null
    }

  def setCaretToStartOnContentChange(comboBox: DistributionComboBox): Unit = {
    comboBox.getEditor.getEditorComponent match {
      case textField: JTextField =>
        setCaretToStartOnContentChange(textField)
      case _ =>
    }
  }

  def setCaretToStartOnContentChange(textField: JTextField): Unit = {
    textField.getDocument match {
      case ad: AbstractDocument =>
        ad.setDocumentFilter(new DocumentFilter() {
          @throws[BadLocationException]
          override def replace(fb: DocumentFilter.FilterBypass, offset: Int, length: Int, text: String, attrs: AttributeSet): Unit = {
            super.replace(fb, offset, length, text, attrs)

            textField.setCaretPosition(0)
          }
        })
      case _ =>
    }
  }
}
