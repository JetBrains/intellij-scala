import sbt.*
import sbt.librarymanagement.DependencyBuilders.OrganizationArtifactName

case class IntellijSdkSubsetInfo(
  artifact: Artifact,
  modulePrefix: OrganizationArtifactName,
  jarsRelativePaths: Seq[String],
) {
  def toMaterialisedInfo(
    buildNumber: String,
    intellijBaseDir: File,
  ): IntellijSdkSubsetInfo.Materialised = IntellijSdkSubsetInfo.Materialised(
    artifact = artifact,
    module = modulePrefix % buildNumber,
    jarFiles = jarsRelativePaths.map(intellijBaseDir / _),
  )
}

object IntellijSdkSubsetInfo {

  private val IntelliJSdkSubsetPrefix = "INTELLIJ-SDK-"

  private def IntelliJSdkSubsetArtifactName(subsetName: String): String =
    IntelliJSdkSubsetPrefix + subsetName

  def apply(subsetName: String, jarsRelativePaths: Seq[String]): IntellijSdkSubsetInfo = {
    val artifactName: String = IntelliJSdkSubsetArtifactName(subsetName)
    val artifact = Artifact(name = artifactName)
    new IntellijSdkSubsetInfo(
      artifact = artifact,
      modulePrefix = "org.jetbrains" % artifactName,
      jarsRelativePaths = jarsRelativePaths
    )
  }

  /**
   * Contains concrete values with all parameters substituted (exact intellij version, intellij SDK base dir)
   */
  case class Materialised(
    jarFiles: Seq[File],
    artifact: Artifact,
    module: ModuleID,
  )

  /**
   * JPS classpath construction logic can be found here:
   *  - com.intellij.compiler.server.impl.BuildProcessClasspathManager.getBuildProcessClasspath
   *  - org.jetbrains.jps.cmdline.ClasspathBootstrap.getBuildProcessApplicationClasspath
   *  - com.intellij.compiler.server.impl.BuildProcessClasspathManager.getBuildProcessPluginsClasspath
   *
   * An easy practical way to see which classpath is actually used in the process is to place a breakpoint inside
   * BuildProcessClasspathManager.getBuildProcessClasspath
   *
   * Note that the JPS process will contain classpath from other plugins as well.
   * Currently, only base classes from Java & Platform are required for Scala Plugin
   *
   * TODO: can we read the base classpath from the product.info?
   */
  val Jps: IntellijSdkSubsetInfo = IntellijSdkSubsetInfo(
    subsetName = "jps",
    jarsRelativePaths = Seq(
      /** see also org.jetbrains.plugins.scala.compiler.CompileServerLauncher.compileServerJars */
      "lib/util.jar",
      "lib/util-8.jar",
      "lib/util_rt.jar",
      "lib/protobuf.jar",
      "lib/jps-model.jar",
      "lib/forms_rt.jar",
      "lib/idea_rt.jar",

      //If you need any extra plugin dependencies, add the jars here
      "plugins/java/lib/jps-builders.jar",
      "plugins/java/lib/jps-builders-6.jar",
      "plugins/java/lib/jps-javac-extension.jar",
      "plugins/java/lib/javac2.jar",
      "plugins/java/lib/aether-dependency-resolver.jar",
    ),
  )

  val JpsShared: IntellijSdkSubsetInfo = IntellijSdkSubsetInfo(
    subsetName = "jps-shared",
    jarsRelativePaths = Seq(
      "lib/app.jar",
      "lib/app-client.jar",
      "lib/util.jar",
      "lib/util-8.jar",
      "lib/util_rt.jar",
    ),
  )
}