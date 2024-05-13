package org.jetbrains.plugins.scala.actions

import com.intellij.ide.fileTemplates.actions.AttributesDefaults
import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.sbt.icons.Icons

final class NewSbtPluginFileAction extends NewPredefinedSbtFileAction(
  ScalaBundle.message("newclassorfile.menu.action.plugin.sbt.text"),
  ScalaBundle.message("newclassorfile.menu.action.sbt.description"),
  Icons.SBT_FILE,
  ScalaBundle.message("newclassorfile.menu.action.plugin.sbt.defaultName")
) {
  protected override def getAttributesDefaults(dataContext: DataContext): AttributesDefaults = new AttributesDefaults(ScalaBundle.message("newclassorfile.menu.action.plugin.sbt.defaultName")).withFixedName(true)
}
