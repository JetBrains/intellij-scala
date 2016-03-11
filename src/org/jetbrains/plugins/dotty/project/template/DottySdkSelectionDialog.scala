package org.jetbrains.plugins.dotty.project.template

import javax.swing.JComponent

import com.intellij.util.ui.ListTableModel
import org.jetbrains.plugins.dotty.project.DottyVersions
import org.jetbrains.plugins.scala.project.template.{SdkTableModel, SdkChoice, SdkSelectionDialog}

import scala.runtime.BoxedUnit

/**
  * @author adkozlov
  */
class DottySdkSelectionDialog(parent: JComponent, provider: () => java.util.List[SdkChoice])
  extends SdkSelectionDialog(parent, provider, false) {

  setDownloadButtonText("Download latest snapshot")

  override protected def getLanguageName = "Dotty"

  override protected def fetchVersions(): ((String) => BoxedUnit) => Array[String] = {
    case _ => DottyVersions.loadDottyVersions
  }

  override protected def getSdkTableModel: ListTableModel[SdkChoice] = new DottySdkTableModel()

  override protected def downloadVersion(version: String): ((String) => BoxedUnit) => BoxedUnit = {
    case listener =>
      DottyDownloader.downloadDotty(version, s => listener(s))
      BoxedUnit.UNIT
  }
}
