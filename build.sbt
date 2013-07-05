organization := "org.jetbrains"

version := sys.env.get("BUILD_NUMBER").getOrElse("0.1-SNAPSHOT")
