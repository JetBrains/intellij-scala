name := "sbt-intellij"

scalaVersion := "2.10.2"

scalacOptions := Seq("-language:implicitConversions", "-language:reflectiveCalls", "-language:postfixOps")

unmanagedJars in Compile <++= ideaDirectory.map(base => ((base / "lib") ** "*.jar").classpath)

unmanagedJars in Compile <++= scalaPluginDirectory.map(base => ((base / "out" / "cardea" / "artifacts" / "Scala" / "lib") ** "*.jar").classpath)
