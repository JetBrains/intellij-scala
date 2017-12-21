organization := "com.typesafe.akka.samples"
name := "akka-samples"

scalaVersion := "2.12.2"
val akkaVersion = "2.5.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-camel" % akkaVersion,
  "org.apache.camel" % "camel-jetty" % "2.17.7",
  "org.apache.camel" % "camel-quartz" % "2.17.7",
  "org.slf4j" % "slf4j-api" % "1.7.23",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.1",
  "io.kamon" % "sigar-loader" % "1.6.6-rev002",
  "junit" % "junit" % "4.11" % Test,
  "com.novocode" % "junit-interface" % "0.10" % Test,
  "org.iq80.leveldb" % "leveldb" % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"
)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
