name := "scala-jps-plugin"

organization := "JetBrains"

scalaVersion := "2.11.2"

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / "SDK/sbt" * "*.jar").classpath

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / "SDK/nailgun" * "*.jar").classpath

unmanagedSourceDirectories in Compile += baseDirectory.value / "src"

mappings in (Compile, packageBin) ++= {
  val base = baseDirectory.value
  for {
    (file, rp) <- (base / "src" / "META-INF" *** ) x relativeTo(base / "src")
  } yield file -> rp
}
