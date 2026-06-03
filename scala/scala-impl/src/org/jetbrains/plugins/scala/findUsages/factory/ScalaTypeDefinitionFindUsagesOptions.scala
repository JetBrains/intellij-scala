package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.findUsages.factory.ScalaTypeDefinitionFindUsagesOptions._

final class ScalaTypeDefinitionFindUsagesOptions(project: Project) extends ScalaFindUsagesOptionsBase(project, isSearchForTextOccurrencesDefault = false) {
  var isImplementingTypeDefinitions: Boolean = isImplementingTypeDefinitionsDefault
  var isMembersUsages: Boolean = isMembersUsagesDefault
  var isSearchCompanionModule: Boolean = isSearchCompanionModuleDefault
  var isOnlyNewInstances: Boolean = isOnlyNewInstancesDefault


  override def setDefaults(properties: PropertiesComponent, prefix: String): Unit = {
    super.setDefaults(properties, prefix)
    isImplementingTypeDefinitions = properties.getBoolean(prefix + "isImplementingTypeDefinitions", isImplementingTypeDefinitionsDefault)
    isMembersUsages = properties.getBoolean(prefix + "isMembersUsages", isMembersUsagesDefault)
    isSearchCompanionModule = properties.getBoolean(prefix + "isSearchCompanionModule", isSearchCompanionModuleDefault)
    isOnlyNewInstances = properties.getBoolean(prefix + "isOnlyNewInstances", isOnlyNewInstancesDefault)
  }

  override def storeDefaults(properties: PropertiesComponent, prefix: String): Unit = {
    super.storeDefaults(properties, prefix)
    properties.setValue(prefix + "isImplementingTypeDefinitions", isImplementingTypeDefinitions, isImplementingTypeDefinitionsDefault)
    properties.setValue(prefix + "isMembersUsages", isMembersUsages, isMembersUsagesDefault)
    properties.setValue(prefix + "isSearchCompanionModule", isSearchCompanionModule, isSearchCompanionModuleDefault)
    properties.setValue(prefix + "isOnlyNewInstances", isOnlyNewInstances, isOnlyNewInstancesDefault)
  }

  override def equals(o: Any): Boolean = {
    o match {
      case other: ScalaTypeDefinitionFindUsagesOptions =>
        super.equals(o) &&
          other.isImplementingTypeDefinitions == isImplementingTypeDefinitions &&
          other.isMembersUsages == isMembersUsages &&
          other.isSearchCompanionModule == isSearchCompanionModule &&
          other.isOnlyNewInstances == isOnlyNewInstances
      case _ => false
    }
  }

  override def hashCode(): Int = {
    var res = super.hashCode()
    res = 31 * res + (if (isImplementingTypeDefinitions) 1 else 0)
    res = 31 * res + (if (isMembersUsages) 1 else 0)
    res = 31 * res + (if (isSearchCompanionModule) 1 else 0)
    res = 31 * res + (if (isOnlyNewInstances) 1 else 0)
    res
  }
}

object ScalaTypeDefinitionFindUsagesOptions {
  val isImplementingTypeDefinitionsDefault: Boolean = false
  val isMembersUsagesDefault: Boolean = false
  val isSearchCompanionModuleDefault: Boolean = false
  val isOnlyNewInstancesDefault: Boolean = false
}