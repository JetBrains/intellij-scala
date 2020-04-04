package org.jetbrains.plugins.scala.project.sdkdetect.repository
import java.nio.file.{Path, Paths}
import java.util.stream.{Stream => JStream}

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.scala.project.template.{PathExt, _}

object SdkmanDetector extends ScalaSdkDetector {
  private val SDKMAN_DIR_ENV     = "SDKMAN_DIR"
  private val SDKMAN_DEFAULT_DIR = sys.props.get("user.home").map(x => Paths.get(x) / ".sdkman")
  override def friendlyName: String = "SDKMAN!"
  override def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = SdkmanSdkChoice(descriptor)
  override def buildJarStream(implicit indicator: ProgressIndicator): JStream[Path] = {
    val sdkmanHome = sys.env.get(SDKMAN_DIR_ENV)
      .map(x => Paths.get(x))
      .orElse(SDKMAN_DEFAULT_DIR)
      .filter(_.exists)
    val scalaRoot = sdkmanHome.map(_ / "candidates" / "scala")
    scalaRoot.filter(_.exists).map(collectJarFiles).getOrElse(JStream.empty())
  }
}
