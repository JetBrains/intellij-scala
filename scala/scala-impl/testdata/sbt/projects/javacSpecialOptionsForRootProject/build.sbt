name := "javac-special-options-for-root-project"
version := "0.1"
scalaVersion := "2.13.4"

javacOptions ++= Seq(
  "-g:none",
  "-nowarn",
  "--enable-preview", // will be ignored, cause JDK 9 doesn't have preview features
  "-deprecation",
  "-target", "1.7",
  "-source", "9",
  "-Werror"
)