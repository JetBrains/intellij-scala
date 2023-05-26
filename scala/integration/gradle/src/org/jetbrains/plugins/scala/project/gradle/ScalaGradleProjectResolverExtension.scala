package org.jetbrains.plugins.scala.project.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.{ExternalSystemConstants, Order}
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.model.data.{ScalaCompileOptionsData, ScalaModelData}
import org.jetbrains.plugins.gradle.model.scala.{ScalaCompileOptions, ScalaForkOptions, ScalaModel}
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.util.GradleConstants

import java.util
import java.util.Collections
import scala.annotation.nowarn

@Order(ExternalSystemConstants.UNORDERED)
final class ScalaGradleProjectResolverExtension extends AbstractProjectResolverExtension {
  override def populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode[ModuleData]): Unit = {
    Option(resolverCtx.getExtraProject(gradleModule, classOf[ScalaModel])).foreach { scalaModel =>
      ideModule.createChild(ScalaModelData.KEY, dataOf(scalaModel))
    }
    nextResolver.populateModuleExtraModels(gradleModule, ideModule)
  }

  override def getExtraProjectModelClasses: util.Set[Class[_]] = Collections.singleton(classOf[ScalaModel])

  private def dataOf(model: ScalaModel): ScalaModelData = {
    val data = new ScalaModelData(GradleConstants.SYSTEM_ID);
    data.setZincClasspath(model.getZincClasspath)
    data.setScalaClasspath(model.getScalaClasspath)
    data.setScalaCompileOptions(Option(model.getScalaCompileOptions).map(dataOf).orNull)
    data.setSourceCompatibility(model.getSourceCompatibility)
    data.setTargetCompatibility(model.getTargetCompatibility)
    data
  }

  private[this] def dataOf(options: ScalaCompileOptions): ScalaCompileOptionsData = {
    val data = new ScalaCompileOptionsData;
    data.setAdditionalParameters(options.getAdditionalParameters)
    data.setDaemonServer(options.getDaemonServer: @nowarn("cat=deprecation"))
    data.setDebugLevel(options.getDebugLevel)
    data.setDeprecation(options.isDeprecation)
    data.setEncoding(options.getEncoding)
    data.setFailOnError(options.isFailOnError)
    data.setForce(options.getForce)
    data.setFork(options.isFork: @nowarn("cat=deprecation"))
    data.setListFiles(options.isListFiles)
    data.setLoggingLevel(options.getLoggingLevel)
    data.setDebugLevel(options.getDebugLevel)
    data.setLoggingPhases(options.getLoggingPhases)
    data.setOptimize(options.isOptimize)
    data.setUnchecked(options.isUnchecked)
    data.setUseAnt(options.isUseAnt: @nowarn("cat=deprecation"))
    data.setUseCompileDaemon(options.isUseCompileDaemon: @nowarn("cat=deprecation"))
    data.setForkOptions(Option(options.getForkOptions).map(dataOf).orNull)
    data
  }

  private[this] def dataOf(options: ScalaForkOptions): ScalaCompileOptionsData.ScalaForkOptions = {
    val data = new ScalaCompileOptionsData.ScalaForkOptions;
    data.setJvmArgs(options.getJvmArgs)
    data.setMemoryInitialSize(options.getMemoryInitialSize)
    data.setMemoryMaximumSize(options.getMemoryMaximumSize)
    data
  }
}
