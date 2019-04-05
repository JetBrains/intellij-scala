package org.jetbrains.plugins.scala
package project
package template

import java.lang.Boolean

import com.intellij.util.ui.{ColumnInfo, ListTableModel}

/**
 * @author Pavel Fatin
 */
final class SdkTableModel extends ListTableModel[SdkChoice](
  new ColumnInfo[SdkChoice, String]("Location") {
    override def valueOf(item: SdkChoice): String = item.source

    override def getPreferredStringValue = "Maven"
  },
  new ColumnInfo[SdkChoice, String]("Version") {

    import Version._

    override def valueOf(item: SdkChoice): String = item.sdk.version.fold(Default)(abbreviate)

    override def getPreferredStringValue = "2.11.0"
  },
  new SdkTableModel.BooleanColumnInfo("Sources") {
    override def valueOf(item: SdkChoice): Boolean = item.sdk.sourceFiles.nonEmpty
  },
  new SdkTableModel.BooleanColumnInfo("Docs") {
    override def valueOf(item: SdkChoice): Boolean = item.sdk.docFiles.nonEmpty
  })

object SdkTableModel {

  private abstract class BooleanColumnInfo(name: String) extends ColumnInfo[SdkChoice, Boolean](name) {
    override final def getColumnClass: Class[Boolean] = classOf[Boolean]

    override final def getPreferredStringValue = "0"
  }

}