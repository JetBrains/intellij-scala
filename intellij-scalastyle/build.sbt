name := "intellij-scalastyle"

organization := "JetBrains"

scalaVersion := "2.11.2"

def readIdeaPropery(key: String): String = {
  import java.util.Properties
  val prop = new Properties()
  IO.load(prop, file("idea.properties"))
  prop.getProperty(key)
}

lazy val ideaBasePath = "SDK/ideaSDK/idea-" + readIdeaPropery( "ideaVersion")

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / ideaBasePath / "lib" * "*.jar").classpath

unmanagedJars in Compile ++= (baseDirectory.value / "jars" * "*.jar").classpath

mappings in (Compile, packageBin) ++= {
  val base = baseDirectory.value
  for {
    (file, rp) <- (base / "META-INF" * "*.xml") x relativeTo(base)
  } yield file -> rp
}
