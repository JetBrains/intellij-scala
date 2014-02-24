package org.jetbrains.plugins.scala
package lang.formatting.automatic.settings

import com.intellij.openapi.components._
import com.intellij.openapi.project.Project

/**
 * @author Roman.Shein
 *         Date: 19.02.14
 */
@State(name = "ScalaFormattingSettings",
  storages = Array(
    new Storage(file = "$WORKSPACE_FILE$"),
    new Storage(file = "$PROJECT_CONFIG_DIR$/scala_settings.xml",
    scheme = StorageScheme.DIRECTORY_BASED))
)
class ScalaFormattingSettingsSerializer extends PersistentStateComponent[FormattingSettings]{

  private var state: FormattingSettings = null

  override def loadState(state: FormattingSettings): Unit = this.state = state

  override def getState: FormattingSettings = state
}

object ScalaFormattingSettingsSerializer {

  def getInstance(project: Project): ScalaFormattingSettingsSerializer = //instance
    ServiceManager.getService(project, classOf[ScalaFormattingSettingsSerializer])
}
