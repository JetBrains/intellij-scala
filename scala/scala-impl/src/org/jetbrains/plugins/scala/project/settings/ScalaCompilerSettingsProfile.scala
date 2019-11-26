package org.jetbrains.plugins.scala.project.settings

import java.util
import java.util.Collections

/**
 * @author Pavel Fatin
 */
// TODO This class is needed for the "imported" ScalaCompilerConfigurationPanel.
// TODO It's better to replace it with immutable case classes later.
class ScalaCompilerSettingsProfile(name: String) {
  private var myName: String = name
  private var myModuleNames = new util.ArrayList[String]
  private var mySettings = new ScalaCompilerSettings

  def getName: String = myName

  def initFrom(profile: ScalaCompilerSettingsProfile): Unit = {
    ScalaCompilerConfiguration.incModificationCount()
    myName = profile.getName
    mySettings = profile.getSettings
    myModuleNames = new util.ArrayList[String](profile.getModuleNames)
  }

  def getModuleNames: util.List[String] = Collections.unmodifiableList(myModuleNames)

  def addModuleName(name: String): Unit = {
    ScalaCompilerConfiguration.incModificationCount()
    myModuleNames.add(name)
  }

  def removeModuleName(name: String): Unit = {
    ScalaCompilerConfiguration.incModificationCount()
    myModuleNames.remove(name)
  }

  def getSettings: ScalaCompilerSettings = mySettings

  def setSettings(settigns: ScalaCompilerSettings): Unit = {
    ScalaCompilerConfiguration.incModificationCount()
    mySettings = settigns
  }

  override def toString: String = myName
}
