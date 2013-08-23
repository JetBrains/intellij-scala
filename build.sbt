organization := "org.jetbrains"

version := sys.env.get("BUILD_NUMBER").getOrElse("0.1-SNAPSHOT")

javaHome in Global := sys.env.get("JAVA_HOME").map(file)
