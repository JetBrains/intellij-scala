package org.jetbrains.plugins.scala
package project
package settings

import com.intellij.openapi.components._
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.{ModificationTracker, SimpleModificationTracker}
import com.intellij.util.xmlb.{SkipDefaultValuesSerializationFilters, XmlSerializer}
import org.jdom.Element
import org.jetbrains.annotations.TestOnly

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

/**
  * @author Pavel Fatin
  */
@State(
  name = "ScalaCompilerConfiguration",
  storages = Array(new Storage("scala_compiler.xml")),
  //reportStatistic = true // TODO: will not be recorded due to state is Element
)
class ScalaCompilerConfiguration(project: Project) extends PersistentStateComponent[Element] with ModificationTracker {

  var incrementalityType: IncrementalityType = IncrementalityType.IDEA

  var defaultProfile: ScalaCompilerSettingsProfile = new ScalaCompilerSettingsProfile("Default")

  var customProfiles: collection.Seq[ScalaCompilerSettingsProfile] = Seq.empty

  @TestOnly
  def createCustomProfileForModule(profileName: String, module: Module): ScalaCompilerSettingsProfile = {
    assert(!allProfiles.exists(_.getName == profileName))
    val profile = new ScalaCompilerSettingsProfile(profileName)
    val moduleName = module.getName
    profile.addModuleName(moduleName)
    customProfiles = customProfiles :+ profile
    allProfiles.foreach(_.removeModuleName(moduleName))
    ScalaCompilerConfiguration.incModificationCount()
    profile
  }

  def getProfileForModule(module: Module): ScalaCompilerSettingsProfile =
    customProfiles.find(_.moduleNames.contains(module.getName)).getOrElse(defaultProfile)

  def getSettingsForModule(module: Module): ScalaCompilerSettings =
    getProfileForModule(module).getSettings

  def allCompilerPlugins: collection.Seq[String] = (allProfiles).map(_.getSettings).flatMap(_.plugins)

  private def allProfiles: collection.Seq[ScalaCompilerSettingsProfile] = defaultProfile +: customProfiles

  def hasSettingForHighlighting(module: Module)
                               (hasSetting: ScalaCompilerSettings => Boolean): Boolean =
    settingsForHighlighting(module).exists(hasSetting)

  //currently we cannot rely on compiler options for shared source modules
  def settingsForHighlighting(module: Module): collection.Seq[ScalaCompilerSettings] = {
    val modules = module.getModuleTypeName match{
      case "SHARED_SOURCES_MODULE" =>
        ModuleManager.getInstance(module.getProject).getModuleDependentModules(module).asScala
      case _ => Seq(module)
    }

    modules.map(getSettingsForModule)
  }

  def configureSettingsForModule(module: Module, source: String, settings: ScalaCompilerSettings): Unit = {
    customProfiles.foreach { profile =>
      profile.removeModuleName(module.getName)
      if (profile.getName.startsWith(source) && profile.moduleNames.isEmpty) {
        customProfiles = customProfiles.filterNot(_ == profile)
      }
    }
    customProfiles.find(_.getSettings.toState == settings.toState) match {
      case Some(profile) => profile.addModuleName(module.getName)
      case None =>
        val profileNames = customProfiles.iterator.map(_.getName).filter(_.startsWith(source)).toSet
        @tailrec def firstFreeName(i: Int): String = {
          val name = source + " " + i
          if (profileNames.contains(name)) firstFreeName(i + 1) else name
        }
        val profile = new ScalaCompilerSettingsProfile(firstFreeName(1))
        profile.setSettings(settings)
        profile.addModuleName(module.getName)
        customProfiles = (customProfiles :+ profile).sortBy(_.getName)
    }
  }

  override def getState: Element = {
    val configurationElement = XmlSerializer.serialize(defaultProfile.getSettings.toState, new SkipDefaultValuesSerializationFilters())

    if (incrementalityType != IncrementalityType.IDEA) {
      val incrementalityTypeElement = new Element("option")
      incrementalityTypeElement.setAttribute("name", "incrementalityType")
      incrementalityTypeElement.setAttribute("value", incrementalityType.toString)
      configurationElement.addContent(incrementalityTypeElement)
    }

    customProfiles.foreach { profile =>
      val profileElement = XmlSerializer.serialize(profile.getSettings.toState, new SkipDefaultValuesSerializationFilters())
      profileElement.setName("profile")
      profileElement.setAttribute("name", profile.getName)
      profileElement.setAttribute("modules", profile.moduleNames.sorted.mkString(","))

      configurationElement.addContent(profileElement)
    }

    configurationElement
  }

  override def loadState(configurationElement: Element): Unit = {
    incrementalityType = configurationElement.getChildren("option").asScala
      .find(_.getAttributeValue("name") == "incrementalityType")
      .map(it => IncrementalityType.valueOf(it.getAttributeValue("value")))
      .getOrElse(IncrementalityType.IDEA)

    defaultProfile.setSettings(ScalaCompilerSettings.fromState(XmlSerializer.deserialize(configurationElement, classOf[ScalaCompilerSettingsState])))

    customProfiles = configurationElement.getChildren("profile").asScala.map { profileElement =>
      val profile = new ScalaCompilerSettingsProfile(profileElement.getAttributeValue("name"))

      val settings = ScalaCompilerSettings.fromState(XmlSerializer.deserialize(profileElement, classOf[ScalaCompilerSettingsState]))
      profile.setSettings(settings)

      val moduleNames = profileElement.getAttributeValue("modules").split(",").filter(!_.isEmpty)
      moduleNames.foreach(profile.addModuleName)

      profile
    }
  }

  override def getModificationCount: Long =
    ScalaCompilerConfiguration.getModificationCount + ProjectRootManager.getInstance(project).getModificationCount
}

object ScalaCompilerConfiguration extends SimpleModificationTracker {
  def instanceIn(project: Project): ScalaCompilerConfiguration =
    project.getService(classOf[ScalaCompilerConfiguration])

  def modTracker(project: Project): ModificationTracker = instanceIn(project)

  //to call as static method from java
  override def incModificationCount(): Unit = super.incModificationCount()

  def hasCompilerPlugin(module: Module, pattern: String): Boolean = {
    val config = instanceIn(module.getProject)
    val settings = config.getSettingsForModule(module)
    settings.plugins.exists(_.contains(pattern))
  }
}
