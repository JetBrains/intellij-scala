package org.jetbrains.bsp.project.test.environment

import java.net.URI
import ch.epfl.scala.bsp4j.JvmEnvironmentItem

import java.nio.file.{Path, Paths}
import scala.jdk.CollectionConverters._

case class JvmEnvironment(
  classpath: Seq[String],
  workdir: Path,
  environmentVariables: Map[String, String],
  jvmOptions: Seq[String]
)

object JvmEnvironment {

  /** workingDirectory in JvmEnvironmentItem is not properly specified as uri or path,
   * and may be inconsistent between build tools */
  private def fromUriOrPath(uriOrPath: String) = {
    if (uriOrPath.startsWith("file:")) Path.of(URI.create(uriOrPath))
    else Paths.get(uriOrPath)
  }

  def fromBsp(environment: JvmEnvironmentItem): JvmEnvironment = {
    JvmEnvironment(
      classpath = environment.getClasspath.asScala.map(x => new URI(x).getPath).toSeq,
      workdir = fromUriOrPath(environment.getWorkingDirectory),
      environmentVariables = environment.getEnvironmentVariables.asScala.toMap,
      jvmOptions = environment.getJvmOptions.asScala.toList
    )
  }
}