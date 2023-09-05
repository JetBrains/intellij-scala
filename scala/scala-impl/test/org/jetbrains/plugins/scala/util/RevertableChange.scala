package org.jetbrains.plugins.scala.util

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.{ApplicationEx, ApplicationManagerEx}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.{Registry, RegistryValue}
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.RevertableChange.CompositeRevertableChange

trait RevertableChange {

  def applyChange(): Unit

  def revertChange(): Unit

  /**
   * Applies the change and automatically reverts it when test disposable is disposed<br>
   * (it's done in tearDown method of test case)
   *
   * @param parentDisposable most commonly UsefulTestCase.getTestRootDisposable
   */
  final def applyChange(parentDisposable: Disposable): Unit = {
    applyChange()
    Disposer.register(parentDisposable, () => {
      revertChange()
    })
  }

  final def applyChange(testCase: UsefulTestCase): Unit = {
    applyChange(testCase.getTestRootDisposable)
  }

  final def apply(body: => Any): Unit =
    run(body)

  final def run(body: => Any): Unit = {
    this.applyChange()
    try
      body
    finally
      this.revertChange()
  }

  final def |+|(change: RevertableChange): RevertableChange = {
    val changes = this match {
      case composite: CompositeRevertableChange => composite.changes :+ change
      case _ => Seq(this, change)
    }
    new CompositeRevertableChange(changes)
  }
}

object RevertableChange {
  object NoOpRevertableChange extends RevertableChange {
    override def applyChange(): Unit = ()

    override def revertChange(): Unit = ()
  }

  final class CompositeRevertableChange(val changes: Seq[RevertableChange]) extends RevertableChange {
    override def applyChange(): Unit = changes.foreach(_.applyChange())

    override def revertChange(): Unit = changes.reverse.foreach(_.revertChange())
  }

  def withModifiedRegistryValue(key: String, newValue: Boolean): RevertableChange =
    withModifiedRegistryValueInternal[Boolean](key, newValue, _.asBoolean, _ setValue _)

  def withModifiedSystemProperty(key: String, newValue: String): RevertableChange =
    withModifiedSetting[String](
      getter = System.getProperty(key),
      setter = value => {
        if (value == null)
          System.clearProperty(key)
        else
          System.setProperty(key, value)
      },
      newValue
    )

  def withModifiedRegistryValue(key: String, newValue: Int): RevertableChange =
    withModifiedRegistryValueInternal[Int](key, newValue, _.asInteger(), _ setValue _)

  private def withModifiedRegistryValueInternal[A](
    key: String,
    newValue: A,
    getter: RegistryValue => A,
    setter: (RegistryValue, A) => Unit
  ): RevertableChange =
    new RevertableChange {
      private var before: Option[A] = None

      override def applyChange(): Unit = {
        val registryValue = Registry.get(key)
        before = Some(getter(registryValue))
        setter(registryValue, newValue)
      }

      override def revertChange(): Unit =
        before.foreach { oldValue =>
          setter(Registry.get(key), oldValue)
        }
    }

  def withModifiedSetting[Settings, T](instance: => Settings)
                                      (value: T)
                                      (get: Settings => T, set: (Settings, T) => Unit): RevertableChange =
    new RevertableChange {
      private var before: Option[T] = None

      override def applyChange(): Unit = {
        before = Some(get(instance))
        set(instance, value)
      }

      override def revertChange(): Unit =
        before.foreach(set(instance, _))
    }

  def withModifiedSetting[A](getter: => A, setter: A => Unit, newValue: A): RevertableChange =
    new RevertableChange {
      private var before: Option[A] = None

      override def applyChange(): Unit = {
        before = Some(getter)
        setter(newValue)
      }

      override def revertChange(): Unit =
        before.foreach(setter)
    }

  def withApplicationSettingsSaving: RevertableChange = new RevertableChange {
    private var saveAllowedBefore: Boolean = _
    private lazy val application: ApplicationEx = ApplicationManagerEx.getApplicationEx

    override def applyChange(): Unit = {
      saveAllowedBefore = application.isSaveAllowed
      application.setSaveAllowed(true)
      application.saveSettings()
    }

    override def revertChange(): Unit = {
      application.setSaveAllowed(saveAllowedBefore)
      application.saveSettings()
    }
  }


  def withModifiedCodeInsightSettings[T](
    get: CodeInsightSettings => T,
    set: (CodeInsightSettings, T) => Unit,
    value: T
  ): RevertableChange = new RevertableChange {
    private def instance: CodeInsightSettings = CodeInsightSettings.getInstance

    private var before: Option[T] = None

    override def applyChange(): Unit = {
      before = Some(get(instance))
      set(instance, value)
    }

    override def revertChange(): Unit =
      before.foreach(set(instance, _))
  }

  def withModifiedScalaProjectSettings[T](
    project: Project,
    get: ScalaProjectSettings => T,
    set: (ScalaProjectSettings, T) => Unit,
    value: T
  ): RevertableChange = new RevertableChange {
    private def instance: ScalaProjectSettings = ScalaProjectSettings.getInstance(project)

    private var before: Option[T] = None

    override def applyChange(): Unit = {
      before = Some(get(instance))
      set(instance, value)
    }

    override def revertChange(): Unit =
      before.foreach(set(instance, _))
  }

  def withCompilerSettingsModified(
    module: Module,
    getModifiedCopy: ScalaCompilerSettings => ScalaCompilerSettings
  ): RevertableChange = new RevertableChange {
    private lazy val profile = module.scalaCompilerSettingsProfile
    private lazy val oldSettings = profile.getSettings

    override def applyChange(): Unit = {
      val newSettings = getModifiedCopy(oldSettings)
      profile.setSettings(newSettings)
    }

    override def revertChange(): Unit = {
      profile.setSettings(oldSettings)
    }
  }
}
