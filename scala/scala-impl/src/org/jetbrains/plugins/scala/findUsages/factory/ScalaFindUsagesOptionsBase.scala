package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.findUsages.JavaFindUsagesOptions
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class ScalaFindUsagesOptionsBase(project: Project, isSearchForTextOccurrencesDefault: Boolean = true) extends JavaFindUsagesOptions(project) {
  //NOTE: this is the field from base class
  isSearchForTextOccurrences = isSearchForTextOccurrencesDefault

  override def storeDefaults(properties: PropertiesComponent, prefix: String): Unit = {
    super.storeDefaults(properties, prefix)
    properties.setValue(prefix + "isSearchForTextOccurrences", isSearchForTextOccurrences, isSearchForTextOccurrencesDefault)
  }

  override def setDefaults(properties: PropertiesComponent, prefix: String): Unit = {
    super.setDefaults(properties, prefix)
    isSearchForTextOccurrences = properties.getBoolean(prefix + "isSearchForTextOccurrences", isSearchForTextOccurrencesDefault)
  }
}
