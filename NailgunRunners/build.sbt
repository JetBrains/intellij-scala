unmanagedSourceDirectories in Compile += baseDirectory.value / "src"

unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / "SDK/nailgun" * "*.jar").classpath