package org.jetbrains.plugins.scala
package project.template

import java.lang.Boolean

import com.intellij.util.ui.{ColumnInfo, ListTableModel}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.Version

/**
 * @author Pavel Fatin
 */
class SdkTableModel extends ListTableModel[SdkChoice](
  new ColumnInfo[SdkChoice, String]("Location") {
    override def valueOf(item: SdkChoice): String = item.source

    override def getPreferredStringValue = "Maven"
  },
  new ColumnInfo[SdkChoice, String]("Platform") {
    override def valueOf(item: SdkChoice): String = item.sdk.platform.getName

    override def getPreferredStringValue = "Scala"
  },
  new ColumnInfo[SdkChoice, String]("Version") {
    override def valueOf(item: SdkChoice): String =
      item.sdk.version.map(_.presentation |> Version.abbreviate).getOrElse("Unknown")

    override def getPreferredStringValue = "2.11.0"
  },
  new ColumnInfo[SdkChoice, Boolean]("Sources") {
    override def getColumnClass: Class[Boolean] = classOf[java.lang.Boolean]

    override def getPreferredStringValue = "0"

    override def valueOf(item: SdkChoice): Boolean = item.sdk.sourceFiles.nonEmpty
  },
  new ColumnInfo[SdkChoice, Boolean]("Docs") {
    override def getColumnClass: Class[Boolean] = classOf[java.lang.Boolean]

    override def getPreferredStringValue = "0"

    override def valueOf(item: SdkChoice): Boolean = item.sdk.docFiles.nonEmpty
  })