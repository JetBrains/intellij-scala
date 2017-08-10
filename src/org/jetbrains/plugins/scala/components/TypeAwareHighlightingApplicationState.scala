package org.jetbrains.plugins.scala
package components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components._
import org.jetbrains.plugins.scala.statistics.CacheStatistics

/**
 * User: Dmitry Naydanov
 * Date: 11/19/12
 */
@State(
  name = "TypeAwareHighlightingApplicationState",
  storages = Array(new Storage("scala_config.xml"))
)
class TypeAwareHighlightingApplicationState extends ApplicationComponent with 
                                                    PersistentStateComponent[TypeAwareHighlightingApplicationState.TypeAwareHighlightingApplicationSettings] {
  import org.jetbrains.plugins.scala.components.TypeAwareHighlightingApplicationState.TypeAwareHighlightingApplicationSettings
  private var myState = new TypeAwareHighlightingApplicationSettings
  
  private def suggest(): Boolean = myState.getSUGGEST_TYPE_AWARE_HIGHLIGHTING_ENABLED
  def setSuggest(b: Boolean) {
    myState setSUGGEST_TYPE_AWARE_HIGHLIGHTING_ENABLED b
  }

  override def getState: TypeAwareHighlightingApplicationSettings = myState

  override def loadState(state: TypeAwareHighlightingApplicationSettings): Unit = {
    myState = state
  }

  override def getComponentName: String = "TypeAwareHighlightingApplicationState"

  override def initComponent(): Unit = {}

  override def disposeComponent(): Unit = {
    CacheStatistics.printStats()
  }
}

object TypeAwareHighlightingApplicationState {
  class TypeAwareHighlightingApplicationSettings {
    import scala.beans.BeanProperty
    
    @BeanProperty
    var SUGGEST_TYPE_AWARE_HIGHLIGHTING_ENABLED: Boolean = false
  }
  
  def getInstance: TypeAwareHighlightingApplicationState = ApplicationManager.getApplication getComponent classOf[TypeAwareHighlightingApplicationState]
}