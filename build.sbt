organization := "org.jetbrains"

version := sys.env.get("BUILD_NUMBER").getOrElse("SNAPSHOT")
