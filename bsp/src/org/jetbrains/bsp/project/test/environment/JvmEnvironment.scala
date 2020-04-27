package org.jetbrains.bsp.project.test.environment

import java.net.URI

import ch.epfl.scala.bsp4j.JvmEnvironmentItem
import scala.collection.JavaConverters._

case class JvmEnvironment(
  classpath: Seq[String],
  workdir: String,
  environmentVariables: Map[String, String],
  jvmOptions: Seq[String]
)

object JvmEnvironment {
  def fromBsp(environment: JvmEnvironmentItem): JvmEnvironment = {
    JvmEnvironment(
      classpath = environment.getClasspath.asScala.map(x => new URI(x).getPath),
      workdir = environment.getWorkingDirectory,
      environmentVariables = environment.getEnvironmentVariables.asScala.toMap,
      jvmOptions = environment.getJvmOptions.asScala.toList
    )
  }
}