package org.jetbrains.plugins.scala
package worksheet.runconfiguration

import com.intellij.execution.configurations.{ConfigurationType, ConfigurationFactory}
import javax.swing.Icon
import org.jetbrains.plugins.scala.icons.Icons

/**
 * @author Ksenia.Sautina
 * @since 10/16/12
 */
class WorksheetConfigurationType  extends ConfigurationType {
  val confFactory = new WorksheetRunConfigurationFactory(this)

  //todo icon
  def getIcon: Icon = Icons.SCALA_SMALL_LOGO

  def getDisplayName: String = "Worksheet"

  def getConfigurationTypeDescription: String = "Worksheet run configurations"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "WorksheetRunConfiguration"
}
