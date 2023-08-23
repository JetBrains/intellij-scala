package org.jetbrains.plugins.scala.project.sdkdetect.repository
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.SystemProperties
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.template.{PathExt, _}

import java.nio.file.{Path, Paths}
import java.util.stream.{Stream => JStream}

object SdkmanDetector extends ScalaSdkDetectorDependencyManagerBase {
  private val SDKMAN_DIR_ENV     = "SDKMAN_DIR"
  private val SDKMAN_DEFAULT_DIR = Some(SystemProperties.getUserHome).map(x => Paths.get(x) / ".sdkman")

  override def friendlyName: String = ScalaBundle.message("sdkman")

  override protected def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = SdkmanSdkChoice(descriptor)

  override protected def buildJarStream(implicit indicator: ProgressIndicator): JStream[Path] = {
    val sdkmanHome = sys.env.get(SDKMAN_DIR_ENV)
      .map(x => Paths.get(x))
      .orElse(SDKMAN_DEFAULT_DIR)
      .filter(_.exists)
    val scalaRoot = sdkmanHome.map(_ / "candidates" / "scala")
    scalaRoot.filter(_.exists).map(collectJarFiles).getOrElse(JStream.empty())
  }
}
