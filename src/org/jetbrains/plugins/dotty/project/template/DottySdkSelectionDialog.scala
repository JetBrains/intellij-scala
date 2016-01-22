package org.jetbrains.plugins.dotty.project.template

import javax.swing.JComponent

import com.intellij.ui.table.TableView
import org.jetbrains.plugins.dotty.project.DottyVersions
import org.jetbrains.plugins.scala.project.template.{SdkChoice, SdkSelectionDialog}

import scala.runtime.BoxedUnit

/**
  * @author adkozlov
  */
class DottySdkSelectionDialog(parent: JComponent, provider: () => java.util.List[SdkChoice])
  extends SdkSelectionDialog(parent, provider) {

  override protected def getLanguageName = "Dotty"

  override protected def fetchVersions(): ((String) => BoxedUnit) => Array[String] = {
    case _ => DottyVersions.loadDottyVersions
  }

  override protected def downloadVersion(version: String): ((String) => BoxedUnit) => BoxedUnit = {
    case listener =>
      DottyDownloader.downloadDotty(version, s => listener(s))
      BoxedUnit.UNIT
  }

  override protected def getResult(table: TableView[SdkChoice]) = DottySdkSelection.chooseDottySdkFiles(table)
}
