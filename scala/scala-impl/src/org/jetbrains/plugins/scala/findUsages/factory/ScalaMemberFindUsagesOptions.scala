package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project

final class ScalaMemberFindUsagesOptions(project: Project) extends ScalaFindUsagesOptionsBase(project) {

  private val isSearchForBaseMemberStr = "isSearchForBaseMember"
  private val isSearchForBaseMemberDefault = true
  var isSearchForBaseMember: Boolean = isSearchForBaseMemberDefault

  private val isSearchForTextOccurrencesStr = "isSearchForTextOccurrences"
  val isSearchForTextOccurrencesDefault = false
  //NOTE: this is the field from base class
  isSearchForTextOccurrences = isSearchForTextOccurrencesDefault

  override def storeDefaults(properties: PropertiesComponent, prefix: String): Unit = {
    super.storeDefaults(properties, prefix)
    properties.setValue(prefix + isSearchForTextOccurrencesStr, isSearchForTextOccurrences, isSearchForTextOccurrencesDefault)
    properties.setValue(prefix + isSearchForBaseMemberStr, isSearchForBaseMember, isSearchForBaseMemberDefault)
  }

  override def setDefaults(properties: PropertiesComponent, prefix: String): Unit = {
    super.setDefaults(properties, prefix)
    isSearchForTextOccurrences = properties.getBoolean(prefix + isSearchForTextOccurrencesStr, isSearchForTextOccurrencesDefault)
    isSearchForBaseMember = properties.getBoolean(prefix + isSearchForBaseMemberStr, isSearchForBaseMemberDefault)
  }

  override def equals(o: Any): Boolean = {
    super.equals(o) && (o match {
      case that: ScalaMemberFindUsagesOptions =>
        that.isSearchForBaseMember == isSearchForBaseMember
      case _ => false
    })
  }

  override def hashCode(): Int = {
    var result = super.hashCode()
    result = 31 * result + (if (isSearchForBaseMember) 1 else 0)
    result
  }
}


