package org.jetbrains.plugins.scala.compilation

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings

import scala.util.Try

/**
 * @author Nikolay.Tropin
 */
object CompilerTestUtil {

  trait RevertableChange {
    def apply(): Unit
    def revert(): Unit
    final def |+| (change: RevertableChange): RevertableChange = {
      val changes = this match {
        case composite: CompositeRevertableChange => composite.changes :+ change
        case _                                    => Seq(this, change)
      }
      new CompositeRevertableChange(changes)
    }
  }

  class CompositeRevertableChange(val changes: Seq[RevertableChange]) extends RevertableChange {
    override def apply(): Unit = changes.foreach(_.apply())
    override def revert(): Unit = changes.reverse.foreach(_.revert())
  }

  private def compileServerSettings: ScalaCompileServerSettings =
    ScalaCompileServerSettings.getInstance().ensuring(
      _ != null,
      "could not get instance of compileServerSettings. Was plugin artifact built before running test?"
    )

  def withModifiedCompileServerSettings(body: ScalaCompileServerSettings => Unit): RevertableChange = new RevertableChange {
    private var settingsBefore: ScalaCompileServerSettings = _

    override def apply(): Unit = {
      val settings = compileServerSettings
      settingsBefore = XmlSerializerUtil.createCopy(settings)
      body(settings)
    }

    override def revert(): Unit = {
      val settings = compileServerSettings
      XmlSerializerUtil.copyBean(settingsBefore, settings)
    }
  }

  def withEnabledCompileServer(enable: Boolean): RevertableChange = new CompositeRevertableChange(Seq(
    withModifiedCompileServerSettings { settings =>
      settings.COMPILE_SERVER_ENABLED = enable
      settings.COMPILE_SERVER_SHUTDOWN_IDLE = true
      settings.COMPILE_SERVER_SHUTDOWN_DELAY = 30
    },
    new RevertableChange {
      private var saveAllowedBefore: Boolean = _

      override def apply(): Unit = {
        val application = ApplicationManagerEx.getApplicationEx
        saveAllowedBefore = application.isSaveAllowed
        application.setSaveAllowed(true)
        application.saveSettings()
      }

      override def revert(): Unit = {
        val application = ApplicationManagerEx.getApplicationEx
        application.setSaveAllowed(saveAllowedBefore)
        application.saveSettings()
      }
    }
  ))

  def withForcedLanguageLevelForBuildProcess(jdk: Sdk): RevertableChange = new RevertableChange {
    private var jdkBefore: Option[String] = None

    override def apply(): Unit = {
      jdk.getHomeDirectory match {
        case null =>
          throw new RuntimeException(s"Failed to set up JDK, got: $jdk")
        case homeDirectory =>
          val jdkHome = homeDirectory.getCanonicalPath
          val registry = Registry.get("compiler.process.jdk")
          jdkBefore = Try(registry.asString).toOption
          registry.setValue(jdkHome)
      }
    }

    override def revert(): Unit =
      jdkBefore.foreach { jdk =>
        Registry.get("compiler.process.jdk").setValue(jdk)
      }
  }

  def withCompileServerJdk(sdk: Sdk): RevertableChange =
    withModifiedCompileServerSettings { settings =>
      settings.USE_DEFAULT_SDK = false
      settings.COMPILE_SERVER_SDK = sdk.getName
    }
}
