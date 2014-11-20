name := "SBT"

organization := "JetBrains"

scalaVersion := "2.11.2"

libraryDependencies ++= Seq(
  "org.apache.maven.indexer" % "indexer-core" % "5.1.1" % Compile,
  "org.apache.maven.indexer" % "indexer-artifact" % "5.1.1" % Compile,
  "org.apache.maven" % "maven-model" % "3.0.4" % Compile,
  "org.codehaus.plexus" % "plexus-container-default" % "1.5.5" % Compile,
  "org.codehaus.plexus" % "plexus-classworlds" % "2.4" % Compile,
  "org.codehaus.plexus" % "plexus-utils" % "3.0.8" % Compile,
  "org.codehaus.plexus" % "plexus-component-annotations" % "1.5.5" % Compile,
  "org.apache.lucene" % "lucene-core" % "3.6.2" % Compile,
  "org.apache.lucene" % "lucene-highlighter" % "3.6.2" % Compile,
  "org.apache.lucene" % "lucene-memory" % "3.6.2" % Compile,
  "org.apache.lucene" % "lucene-queries" % "3.6.2" % Compile,
  "jakarta-regexp" % "jakarta-regexp" % "1.4" % Compile,
  "org.sonatype.aether" % "aether-api" % "1.13.1" % Compile,
  "org.sonatype.aether" % "aether-util" % "1.13.1" % Compile,
  "org.sonatype.sisu" % "sisu-inject-plexus" % "2.2.3" % Compile,
  "org.sonatype.sisu" % "sisu-inject-bean" % "2.2.3" % Compile,
  ("org.sonatype.sisu" % "sisu-guice" % "3.0.3" classifier "no_aop") % Compile,
  "org.apache.maven.wagon" % "wagon-http" % "2.6" % Compile,
  "org.apache.maven.wagon" % "wagon-http-shared" % "2.6" % Compile,
  "org.apache.maven.wagon" % "wagon-provider-api" % "2.6" % Compile,
  "org.jsoup" % "jsoup" % "1.7.2" % Compile,
  "commons-lang" % "commons-lang" % "2.6" % Compile,
  "commons-io" % "commons-io" % "2.2" % Compile,
  "org.apache.httpcomponents" % "httpclient" % "4.3.1" % Compile,
  "org.apache.httpcomponents" % "httpcore" % "4.3" % Compile,
  "commons-logging" % "commons-logging" % "1.1.3" % Compile,
  "commons-codec" % "commons-codec" % "1.6" % Compile,
  "org.apache.xbean" % "xbean-reflect" % "3.4" % Compile
)
