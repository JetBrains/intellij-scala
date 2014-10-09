name := "intellij-hocon"

organization := "JetBrains"

scalaVersion := "2.11.2"

lazy val ideaBasePath = "SDK/ideaSDK/idea-" +  readIdeaPropery(   "ideaVersion")

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / ideaBasePath / "lib" * "*.jar").classpath

mappings in (Compile, packageBin) ++= {
  val base = baseDirectory.value
  for {
    (file, rp) <- (base / "META-INF" * "*.xml") x relativeTo(base)
  } yield file -> rp
}
