package org.jetbrains.plugins.dotty.project.template

import com.intellij.util.ui.{ColumnInfo, ListTableModel}
import org.jetbrains.plugins.scala.project.template.SdkChoice

/**
  * @author adkozlov
  */
class DottySdkTableModel extends ListTableModel[SdkChoice](
  new ColumnInfo[SdkChoice, String]("Location") {
    override def valueOf(item: SdkChoice) = item.source

    override def getPreferredStringValue = "Maven"
  },
  new ColumnInfo[SdkChoice, String]("Version") {
    override def valueOf(item: SdkChoice) = item.sdk.version.map(_.number).getOrElse("Unknown")

    override def getPreferredStringValue = "2.11.5"
  }) {
}