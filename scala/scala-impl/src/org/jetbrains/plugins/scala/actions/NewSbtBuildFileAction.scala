package org.jetbrains.plugins.scala.actions

import com.intellij.ide.fileTemplates.actions.AttributesDefaults
import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.sbt.icons.Icons

final class NewSbtBuildFileAction extends NewPredefinedSbtFileAction(
  ScalaBundle.message("newclassorfile.menu.action.build.sbt.text"),
  ScalaBundle.message("newclassorfile.menu.action.sbt.description"),
  Icons.SBT_FILE,
  ScalaBundle.message("newclassorfile.menu.action.build.sbt.defaultName")
) {
  protected override def getAttributesDefaults(dataContext: DataContext): AttributesDefaults = new AttributesDefaults(ScalaBundle.message("newclassorfile.menu.action.build.sbt.defaultName")).withFixedName(true)
}
