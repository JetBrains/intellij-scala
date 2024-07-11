package org.jetbrains.plugins.scala.project.settings

import com.intellij.openapi.components._
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.{ModificationTracker, SimpleModificationTracker}
import com.intellij.util.xmlb.{SkipDefaultValuesSerializationFilters, XmlSerializer}
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.compiler.data.ScalaCompilerConfigurationAttributes.{IncrementalityTypeAttr, ModulesAttr, NameAttr, OptionAttr, ProfileAttr, SeparateProdTestSourcesAttr, ValueAttr}
import org.jetbrains.plugins.scala.compiler.data.{IncrementalityType, ScalaCompilerSettingsState}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration.DefaultProfileName

import scala.annotation.{nowarn, tailrec}
import scala.jdk.CollectionConverters._

@State(
  name = "ScalaCompilerConfiguration",
  storages = Array(new Storage("scala_compiler.xml")),
  //reportStatistic = true // TODO: will not be recorded due to state is Element
)
class ScalaCompilerConfiguration(project: Project) extends PersistentStateComponent[Element] with ModificationTracker {

  var incrementalityType: IncrementalityType = IncrementalityType.SBT

  var defaultProfile: ScalaCompilerSettingsProfile = new ScalaCompilerSettingsProfile(DefaultProfileName)

  var customProfiles: Seq[ScalaCompilerSettingsProfile] = Seq.empty

  var separateProdTestSources: Boolean = false

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
    module match {
      case profileAware: CompilerProfileAwareModule =>
        val profileName = profileAware.compilerProfileName
        customProfiles.find(_.getName == profileName).getOrElse(defaultProfile)
      case _                  =>
        customProfiles.find(_.moduleNames.contains(module.getName)).getOrElse(defaultProfile)
    }

  def findByProfileName(profileName: String): Option[ScalaCompilerSettingsProfile] =
    allProfiles.find(_.getName == profileName)

  def getSettingsForModule(module: Module): ScalaCompilerSettings =
    getProfileForModule(module).getSettings

  def allCompilerPlugins: Seq[String] = allProfiles.map(_.getSettings).flatMap(_.plugins)

  def allProfiles: Seq[ScalaCompilerSettingsProfile] = defaultProfile +: customProfiles

  //currently we cannot rely on compiler options for shared source modules
  def settingsForHighlighting(module: Module): Seq[ScalaCompilerSettings] = {
    val modules = module.getModuleTypeName match{
      case "SHARED_SOURCES_MODULE" =>
        ModuleManager.getInstance(module.getProject).getModuleDependentModules(module).asScala
      case _ => Seq(module)
    }

    modules.iterator.map(getSettingsForModule).toSeq
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
    // yes, default profile options are currently serialized as root options of element node
    val configurationElement = XmlSerializer.serialize(defaultProfile.getSettings.toState, new SkipDefaultValuesSerializationFilters(): @nowarn("cat=deprecation"))

    val optionElement = new Element(OptionAttr)

    optionElement.setAttribute(NameAttr, SeparateProdTestSourcesAttr)
    optionElement.setAttribute(ValueAttr, separateProdTestSources.toString)

    if (incrementalityType != IncrementalityType.SBT) {
      optionElement.setAttribute(NameAttr, IncrementalityTypeAttr)
      optionElement.setAttribute(ValueAttr, incrementalityType.toString)
    }

    configurationElement.addContent(optionElement)

    customProfiles.foreach { profile =>
      val profileElement = XmlSerializer.serialize(profile.getSettings.toState, new SkipDefaultValuesSerializationFilters(): @nowarn("cat=deprecation"))
      profileElement.setName(ProfileAttr)
      profileElement.setAttribute(NameAttr, profile.getName)
      profileElement.setAttribute(ModulesAttr, profile.moduleNames.sorted.mkString(","))

      configurationElement.addContent(profileElement)
    }

    configurationElement
  }

  override def loadState(configurationElement: Element): Unit = {
    val optionElements = configurationElement.getChildren(OptionAttr).asScala
    incrementalityType = optionElements
      .find(_.getAttributeValue(NameAttr) == IncrementalityTypeAttr)
      .map(it => IncrementalityType.valueOf(it.getAttributeValue(ValueAttr)))
      .getOrElse(IncrementalityType.SBT)

    separateProdTestSources = optionElements
      .find(_.getAttributeValue(NameAttr) == SeparateProdTestSourcesAttr)
      .exists(it => it.getAttributeValue(ValueAttr).toBoolean)

    defaultProfile.setSettings(ScalaCompilerSettings.fromState(XmlSerializer.deserialize(configurationElement, classOf[ScalaCompilerSettingsState])))

    customProfiles = configurationElement.getChildren(ProfileAttr).asScala.map { profileElement =>
      val profile = new ScalaCompilerSettingsProfile(profileElement.getAttributeValue(NameAttr))

      val settings = ScalaCompilerSettings.fromState(XmlSerializer.deserialize(profileElement, classOf[ScalaCompilerSettingsState]))
      profile.setSettings(settings)

      val moduleNames = profileElement.getAttributeValue(ModulesAttr).split(",").filter(!_.isEmpty)
      moduleNames.foreach(profile.addModuleName)

      profile
    }.toSeq
  }

  override def getModificationCount: Long =
    ScalaCompilerConfiguration.getModificationCount + ProjectRootManager.getInstance(project).getModificationCount
}

object ScalaCompilerConfiguration extends SimpleModificationTracker {

  val DefaultProfileName = "Default"

  def instanceIn(project: Project): ScalaCompilerConfiguration =
    project.getService(classOf[ScalaCompilerConfiguration])
  def apply(project: Project): ScalaCompilerConfiguration =
    instanceIn(project)

  def modTracker(project: Project): ModificationTracker = instanceIn(project)

  //to call as static method from java
  override def incModificationCount(): Unit = super.incModificationCount()

}

trait CompilerProfileAwareModule {
  this: Module =>

  def compilerProfileName: String
}
