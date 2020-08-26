package org.jetbrains.plugins.scala.lang.formatting.settings.inference

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.ide.startup.StartupManagerEx
import com.intellij.openapi.components.{PersistentStateComponent, _}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.formatting.settings.inference.ScalaDocAsteriskAlignStyleIndexer.AsteriskAlignStyle
import org.jetbrains.plugins.scala.lang.formatting.settings.inference.ScalaDocAsteriskAlignStyleIndexer.AsteriskAlignStyle.AlignByColumnThree

import scala.beans.BeanProperty
import scala.jdk.CollectionConverters._

@State(
  name = "CodeStyleSettingsInfer",
  storages = Array[Storage](new Storage(value = StoragePathMacros.WORKSPACE_FILE))
)
final class CodeStyleSettingsInferService(private val project: Project)
  extends PersistentStateComponent[CodeStyleSettingsInferService.State] {

  private val Log = Logger.getInstance(getClass)

  private var state = new CodeStyleSettingsInferService.State
  override def getState: CodeStyleSettingsInferService.State = state
  override def loadState(state: CodeStyleSettingsInferService.State): Unit = this.state = state

  def init(): Unit = {
    if (state.done) {
      Log.info("settings inference skipped: already done")
    } else {
      StartupManagerEx.getInstanceEx(project).runWhenProjectIsInitialized { () =>
        DumbService.getInstance(project).runWhenSmart { () =>
          inferSettings()
          state.done = true
        }
      }
    }
  }

  private def inferSettings(): Unit = {
    modifyCodeStyleSettings { settings =>
      inferBestScaladocAsteriskAlignStyle(settings)
    }
  }

  private def inferBestScaladocAsteriskAlignStyle(settings: CodeStyleSettings): Boolean = {
    val fileIndex = FileBasedIndex.getInstance()
    val indexId = ScalaDocAsteriskAlignStyleIndexer.Id

    val sourcesScope = SourceFilterScope(Seq(ScalaFileType.INSTANCE))(project)

    val alignTypeCounts: Map[AsteriskAlignStyle, Int] =
      fileIndex.getAllKeys(indexId, project).asScala.map { alignType =>
        val occurrences = fileIndex.getValues(indexId, alignType, sourcesScope).asScala
        alignType -> occurrences.foldLeft(0)(_ + _)
      }.filter(_._2 > 0).toMap

    if (alignTypeCounts.nonEmpty) {
      val mostUsedStyle = alignTypeCounts.maxBy(_._2)._1
      Log.info(s"Scaladoc: most used align type: $mostUsedStyle ($alignTypeCounts)")
      val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
      scalaSettings.USE_SCALADOC2_FORMATTING = mostUsedStyle == AlignByColumnThree
      true
    } else {
      Log.info(s"Scaladoc: no comments detected")
      false
    }
  }

  private def modifyCodeStyleSettings(modifier: CodeStyleSettings => Boolean): Unit = {
    val codeStyleSchemesModel = new CodeStyleSchemesModel(project)
    val selectedScheme = codeStyleSchemesModel.getSelectedScheme
    val projectScheme =
      if (codeStyleSchemesModel.isProjectScheme(selectedScheme)) {
        selectedScheme
      } else {
        codeStyleSchemesModel.copyToProject(selectedScheme)
        codeStyleSchemesModel.getProjectScheme
      }

    val settings = projectScheme.getCodeStyleSettings
    if (modifier.apply(settings)) {
      codeStyleSchemesModel.apply()
    }
  }
}

object CodeStyleSettingsInferService {
  class State {
    @BeanProperty
    var done: Boolean = false
  }
}