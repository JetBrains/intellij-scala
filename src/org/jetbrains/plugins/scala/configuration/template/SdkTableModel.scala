package org.jetbrains.plugins.scala
package configuration.template

import com.intellij.util.ui.{ColumnInfo, ListTableModel}

/**
 * @author Pavel Fatin
 */
class SdkTableModel extends ListTableModel[SdkChoice](
  new ColumnInfo[SdkChoice, String]("Location") {
    override def valueOf(item: SdkChoice) = item.source

    override def getPreferredStringValue = "Maven"
  },
  new ColumnInfo[SdkChoice, String]("Version") {
    override def valueOf(item: SdkChoice) = item.sdk.version.value

    override def getPreferredStringValue = "2.11.0"
  },
  new ColumnInfo[SdkChoice, Boolean]("Sources") {
    override def getColumnClass = classOf[java.lang.Boolean]

    override def getPreferredStringValue = "0"

    override def valueOf(item: SdkChoice) = item.sdk.sourceFiles.nonEmpty
  },
  new ColumnInfo[SdkChoice, Boolean]("Docs") {
    override def getColumnClass = classOf[java.lang.Boolean]

    override def getPreferredStringValue = "0"

    override def valueOf(item: SdkChoice): Boolean = item.sdk.docFiles.nonEmpty
  }) {
}
