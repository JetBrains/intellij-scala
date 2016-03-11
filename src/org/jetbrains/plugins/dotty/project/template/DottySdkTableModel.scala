package org.jetbrains.plugins.dotty.project.template

import com.intellij.util.ui.{ColumnInfo, ListTableModel}
import org.jetbrains.plugins.scala.project.template.SdkChoice

/**
  * @author Nikolay.Tropin
  */
class DottySdkTableModel extends ListTableModel[SdkChoice](
  new ColumnInfo[SdkChoice, String]("Location") {
    override def valueOf(item: SdkChoice) = item.source

    override def getPreferredStringValue = "Maven"
  },
  new ColumnInfo[SdkChoice, String]("File") {
    override def valueOf(item: SdkChoice) = {
      item.sdk match {
        case d: DottySdkDescriptor => d.mainDottyJar.map(_.getName).getOrElse("Unknown")
      }
    }

    override def getPreferredStringValue = null
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
  })
