package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.findUsages.factory.ScalaMemberFindUsagesOptions._

final class ScalaMemberFindUsagesOptions(project: Project)
  extends ScalaFindUsagesOptionsBase(project, isSearchForTextOccurrencesDefault = false) {

  var isSearchForBaseMember: Boolean = isSearchForBaseMemberDefault

  override def storeDefaults(properties: PropertiesComponent, prefix: String): Unit = {
    super.storeDefaults(properties, prefix)
    properties.setValue(prefix + "isSearchForBaseMember", isSearchForBaseMember, isSearchForBaseMemberDefault)
  }

  override def setDefaults(properties: PropertiesComponent, prefix: String): Unit = {
    super.setDefaults(properties, prefix)
    isSearchForBaseMember = properties.getBoolean(prefix + "isSearchForBaseMember", isSearchForBaseMemberDefault)
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

object ScalaMemberFindUsagesOptions {
  val isSearchForBaseMemberDefault = true
}