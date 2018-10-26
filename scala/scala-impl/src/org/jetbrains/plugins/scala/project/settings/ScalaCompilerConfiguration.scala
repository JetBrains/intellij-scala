package org.jetbrains.plugins.scala.project.settings

import com.intellij.openapi.components._
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.{ModificationTracker, SimpleModificationTracker}
import com.intellij.util.xmlb.{SkipDefaultValuesSerializationFilters, XmlSerializer}
import org.jdom.Element
import org.jetbrains.plugins.scala.project.IncrementalityType

import scala.annotation.tailrec
import scala.collection.JavaConverters._

/**
  * @author Pavel Fatin
  */
@State(
  name = "ScalaCompilerConfiguration",
  storages = Array(new Storage("scala_compiler.xml"))
)
class ScalaCompilerConfiguration(project: Project) extends PersistentStateComponent[Element] with ModificationTracker {
  var incrementalityType: IncrementalityType = IncrementalityType.IDEA

  var defaultProfile: ScalaCompilerSettingsProfile = new ScalaCompilerSettingsProfile("Default")

  var customProfiles: Seq[ScalaCompilerSettingsProfile] = Seq.empty

  def getSettingsForModule(module: Module): ScalaCompilerSettings = {
    val profile = customProfiles.find(_.getModuleNames.contains(module.getName)).getOrElse(defaultProfile)
    profile.getSettings
  }

  //currently we cannot rely on compiler options for shared source modules
  def hasSettingForHighlighting(module: Module, hasSetting: ScalaCompilerSettings => Boolean): Boolean = {
    def isSharedSources(module: Module) = module.getModuleTypeName == "SHARED_SOURCES_MODULE"

    def dependentModules =
      ModuleManager.getInstance(module.getProject).getModuleDependentModules(module).asScala

    val modules = if (isSharedSources(module)) dependentModules else Seq(module)

    modules.map(getSettingsForModule)
      .exists(hasSetting)
  }

  def configureSettingsForModule(module: Module, source: String, options: Seq[String]) {
    customProfiles.foreach { profile =>
      profile.removeModuleName(module.getName)
      if (profile.getName.startsWith(source) && profile.getModuleNames.isEmpty) {
        customProfiles = customProfiles.filterNot(_ == profile)
      }
    }

    val settings = new ScalaCompilerSettings()
    settings.initFrom(options)

    customProfiles.find(_.getSettings.getState == settings.getState) match {
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

  def getState: Element = {
    val configurationElement = XmlSerializer.serialize(defaultProfile.getSettings.getState, new SkipDefaultValuesSerializationFilters())

    if (incrementalityType != IncrementalityType.IDEA) {
      val incrementalityTypeElement = new Element("option")
      incrementalityTypeElement.setAttribute("name", "incrementalityType")
      incrementalityTypeElement.setAttribute("value", incrementalityType.toString)
      configurationElement.addContent(incrementalityTypeElement)
    }

    customProfiles.foreach { profile =>
      val profileElement = XmlSerializer.serialize(profile.getSettings.getState, new SkipDefaultValuesSerializationFilters())
      profileElement.setName("profile")
      profileElement.setAttribute("name", profile.getName)
      profileElement.setAttribute("modules", profile.getModuleNames.asScala.sorted.mkString(","))

      configurationElement.addContent(profileElement)
    }

    configurationElement
  }

  def loadState(configurationElement: Element) {
    incrementalityType = configurationElement.getChildren("option").asScala
      .find(_.getAttributeValue("name") == "incrementalityType")
      .map(it => IncrementalityType.valueOf(it.getAttributeValue("value")))
      .getOrElse(IncrementalityType.IDEA)

    defaultProfile.setSettings(new ScalaCompilerSettings(XmlSerializer.deserialize(configurationElement, classOf[ScalaCompilerSettingsState])))

    customProfiles = configurationElement.getChildren("profile").asScala.map { profileElement =>
      val profile = new ScalaCompilerSettingsProfile(profileElement.getAttributeValue("name"))

      val settings = new ScalaCompilerSettings(XmlSerializer.deserialize(profileElement, classOf[ScalaCompilerSettingsState]))
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
    ServiceManager.getService(project, classOf[ScalaCompilerConfiguration])

  def modTracker(project: Project): ModificationTracker = instanceIn(project)

  //to call as static method from java
  override def incModificationCount(): Unit = super.incModificationCount()
}
