import org.jetbrains.sbtidea.AbstractSbtIdeaPlugin
import sbt.plugins.JvmPlugin
import sbt.{AutoPlugin, Setting, plugins}

object SbtIdeaPluginExtension extends AbstractSbtIdeaPlugin {
  override def trigger = allRequirements
}
