package org.jetbrains.plugins.scala
package components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components._

/**
 * User: Dmitry Naydanov
 * Date: 11/19/12
 */
@State(name = "TypeAwareHighlightingApplicationState",
  storages = Array(
    new Storage(
      id = "TypeAwareHighlightingApplicationState",
      file = "$APP_CONFIG$/scala_config.xml"
    )
  ))
class TypeAwareHighlightingApplicationState extends ApplicationComponent with 
                                                    PersistentStateComponent[TypeAwareHighlightingApplicationState.TypeAwareHighlightingApplicationSettings] {
  import org.jetbrains.plugins.scala.components.TypeAwareHighlightingApplicationState.TypeAwareHighlightingApplicationSettings
  private var myState = new TypeAwareHighlightingApplicationSettings
  
  def suggest() = myState.getSUGGEST_TYPE_AWARE_HIGHLIGHTING_ENABLED
  def setSuggest(b: Boolean) {
    myState setSUGGEST_TYPE_AWARE_HIGHLIGHTING_ENABLED b
  }
  
  def getState: TypeAwareHighlightingApplicationSettings = myState

  def loadState(state: TypeAwareHighlightingApplicationSettings) {
    myState = state
  }

  def getComponentName = "TypeAwareHighlightingApplicationState"

  def initComponent() {}

  def disposeComponent() {}
}

object TypeAwareHighlightingApplicationState {
  class TypeAwareHighlightingApplicationSettings {
    import scala.beans.BeanProperty
    
    @BeanProperty
    var SUGGEST_TYPE_AWARE_HIGHLIGHTING_ENABLED: Boolean = false
  }
  
  def getInstance = ApplicationManager.getApplication getComponent classOf[TypeAwareHighlightingApplicationState]
}