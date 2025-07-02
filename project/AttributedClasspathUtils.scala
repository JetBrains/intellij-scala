import sbt.Keys.*
import sbt.*

object AttributedClasspathUtils {

  def buildIntellijSdkSubsetAttributedClasspath(
    info: IntellijSdkSubsetInfo.Materialised,
    conf: Configuration
  ): Classpath = {
    buildAttributedClasspath(info.jarFiles)(info.artifact, info.module, conf)
  }

  // This is ca copy of org.jetbrains.sbtidea.tasks.classpath.AttributedClasspathTasks
  // We can consider deduplicating it
  private def buildAttributedClasspath(jars: Seq[File])(
    artifact: Artifact,
    module: ModuleID,
    configuration: Configuration
  ): Classpath = {
    val attributes: AttributeMap = AttributeMap.empty
      .put(Keys.artifact.key, artifact)
      .put(Keys.moduleID.key, module)
      .put(Keys.configuration.key, configuration)
    jars.map(Attributed(_)(attributes))
  }
}