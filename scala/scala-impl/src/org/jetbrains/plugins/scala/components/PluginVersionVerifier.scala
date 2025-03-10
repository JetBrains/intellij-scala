package org.jetbrains.plugins.scala.components

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.plugins.{org => _, _}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.plugins.scala.extensions.invokeLater

object ScalaPluginVersionVerifier {

  class Version(private val major: Int, private val minor: Int, private val build: Int) extends Ordered[Version] with Serializable {
    override def compare(that: Version): Int = implicitly[Ordering[(Int, Int, Int)]]
      .compare((major, minor, build), (that.major, that.minor, that.build))

    val presentation: String = if (major == Int.MaxValue) "SNAPSHOT" else s"$major.$minor.$build"

    override def equals(that: Any): Boolean = compare(that.asInstanceOf[Version]) == 0

    override def toString: String = presentation
  }

  object Version {
    object Snapshot extends Version(Int.MaxValue, Int.MaxValue, Int.MaxValue)
    object Zero extends Version(0,0,0)
    def parse(version: String): Option[Version] = {
      val VersionRegex = "(\\d+)[.](\\d+)[.](\\d+)".r
      version match {
        case "VERSION" | "SNAPSHOT" => Some(Snapshot)
        case VersionRegex(major: String, minor: String, build: String) => Some(new Version(major.toInt, minor.toInt, build.toInt))
        case _ => None
      }
    }
  }

  lazy val getPluginVersion: Option[Version] = {
    getClass.getClassLoader match {
      case pluginLoader: PluginClassLoader =>
        Version.parse(PluginManagerCore.getPlugin(pluginLoader.getPluginId).getVersion)
      case _ => Some(Version.Snapshot)
    }
  }

  def getPluginDescriptor: IdeaPluginDescriptorImpl = {
    getClass.getClassLoader match {
      case pluginLoader: PluginClassLoader =>
        PluginManagerCore.getPlugin(pluginLoader.getPluginId).asInstanceOf[IdeaPluginDescriptorImpl]
      case other =>
        throw new RuntimeException(s"Wrong plugin classLoader: $other")
    }
  }

  def scalaPluginId: PluginId = getPluginDescriptor.getPluginId

  private[components] val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier")
}

class ScalaPluginVersionVerifierActivity extends RunOnceStartupActivity {
  override def doRunActivity(): Unit = {
    invokeLater {
      ScalaPluginUpdater.askUpdatePluginBranchIfNeeded()
      ScalaPluginUpdater.postCheckIdeaCompatibility()
    }
  }

  override protected def doCleanup(): Unit = {}
}
