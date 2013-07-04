name := "intellij-sbt"

organization := "org.jetbrains"

version := "0.1"

scalaVersion := "2.10.2"

unmanagedJars in Compile <++= baseDirectory map { base =>
	val dirs = file("d:\\tools\\idea\\lib") +++ file("D:\\Dropbox\\scala-plugin\\classes\\artifacts\\Scala\\lib")
	(dirs ** "*.jar").classpath
}