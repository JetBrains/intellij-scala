unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / "SDK/nailgun" * "*.jar").classpath

unmanagedSourceDirectories in Compile += baseDirectory.value / "src"
