package org.jetbrains.plugins.scala.packagesearch.configuration

import com.intellij.ide.ui.search.{SearchableOptionContributor, SearchableOptionProcessor}
import com.jetbrains.packagesearch.intellij.plugin.configuration.ui.PackageSearchGeneralConfigurable

class SbtSearchableOptionContributor extends SearchableOptionContributor {
  override def processOptions(processor: SearchableOptionProcessor): Unit = {
    addSearchConfigurationMap(processor, "sbt", "configuration")
  }

  def addSearchConfigurationMap(processor: SearchableOptionProcessor, entries: String*):Unit = {
    entries.foreach(entry => {
      processor.addOptions(entry, null, entry, PackageSearchGeneralConfigurable.ID, null, false)
    })
  }
}
