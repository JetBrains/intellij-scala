name := "idea-runner"

organization := "JetBrains"

scalaVersion := "2.11.2"

def readIdeaPropery(key: String): String = {
  import java.util.Properties
  val prop = new Properties()
  IO.load(prop, file("idea.properties"))
  prop.getProperty(key)
}

lazy val ideaBasePath = "SDK/ideaSDK/idea-"      + readIdeaPropery("ideaVersion")

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / ideaBasePath / "lib" * "*.jar").classpath

unmanagedJars in Compile +=  file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar"

// run configuration

fork in run := true

baseDirectory in run := baseDirectory.value.getParentFile / ideaBasePath / "bin"

mainClass in (Compile, run) := Some("com.intellij.idea.Main")

//unmanagedJars in run ++= (allIdeaJars in Global).value

javaOptions in run ++= Seq(
  "-Xmx800m",
  "-XX:ReservedCodeCacheSize=64m",
  "-XX:MaxPermSize=250m",
  "-XX:+HeapDumpOnOutOfMemoryError",
  "-ea",
  "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005",
  "-Didea.is.internal=true",
  "-Didea.debug.mode=true",
  "-Didea.system.path=/home/miha/.IdeaData/IDEA-14/scala/system",
  "-Didea.config.path=/home/miha/.IdeaData/IDEA-14/scala/config",
  "-Dapple.laf.useScreenMenuBar=true",
  s"-Dplugin.path=${baseDirectory.value.getParentFile.getParentFile.getParent}/out/IDEA-14/artifacts/ScalaDistributed",
  "-Didea.ProcessCanceledException=disabled"
)