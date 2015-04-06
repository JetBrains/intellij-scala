conflictWarning  := ConflictWarning.disable

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

resolvers  += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.11" % "2.2.1" % Compile,
  "org.scalatest" % "scalatest_2.10" % "2.2.1" % Compile,
  "org.specs2" % "specs2_2.11" % "2.4.15" % Compile,
  "org.scalaz" % "scalaz-core_2.11" % "7.1.0" % Compile,
  "org.scalaz" % "scalaz-concurrent_2.11" % "7.1.0" % Compile,
  "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.2" % Compile,
  "org.specs2" % "specs2_2.10" % "2.4.6" % Compile,
  "org.scalaz" % "scalaz-core_2.10" % "7.1.0" % Compile,
  "org.scalaz" % "scalaz-concurrent_2.10" % "7.1.0" % Compile,
  "org.scalaz.stream" % "scalaz-stream_2.11" % "0.6a" % Compile,
  "com.chuusai" % "shapeless_2.11" % "2.0.0" % Compile,
  "org.typelevel" % "scodec-bits_2.11" % "1.1.0-SNAPSHOT" % Compile,
  "org.typelevel" % "scodec-core_2.11" % "1.7.0-SNAPSHOT" % Compile,
  "org.scalatest" % "scalatest_2.11" % "2.1.7" % Compile,
  "org.scalatest" % "scalatest_2.10" % "2.1.7" % Compile,
  "org.scalatest" % "scalatest_2.10" % "1.9.2" % Compile,
  "com.github.julien-truffaut"  %%  "monocle-core"    % "1.2.0-SNAPSHOT",
  "com.github.julien-truffaut"  %%  "monocle-generic" % "1.2.0-SNAPSHOT",
  "com.github.julien-truffaut"  %%  "monocle-macro"   % "1.2.0-SNAPSHOT"
)

dependencyOverrides += "org.scalatest" % "scalatest_2.10" % "2.1.7"

dependencyOverrides += "org.scalatest" % "scalatest_2.11" % "2.1.7"

dependencyOverrides += "org.scalatest" % "scalatest_2.10" % "1.9.2"

dependencyOverrides += "com.chuusai" % "shapeless_2.11" % "2.0.0"

libraryDependencies += "io.spray" %% "spray-routing" % "1.3.1"
