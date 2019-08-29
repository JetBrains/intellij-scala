package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.components._
import com.intellij.openapi.project.Project

// TODO Fine-grained features: completion, goto, highlighting, etc (enum, EnumSet).
// TODO Persist the state.
// TODO Support listeners.
@State(name = "ScalaProjectSettings", storages = Array(new Storage("scala_features.xml")))
class ScalaFeatureSettings(project: Project) extends ProjectComponent {
    var enabled = false
}

object ScalaFeatureSettings {
    def instanceIn(project: Project): ScalaFeatureSettings =
        ServiceManager.getService(project, classOf[ScalaFeatureSettings])
          .ensuring(_ != null)
}
