resolvers += Resolver.url("jetbrains-bintray",
  url("http://dl.bintray.com/jetbrains/sbt-plugins/"))(Resolver.ivyStylePatterns)

resolvers += Resolver.url("dancingrobot84-bintray",
  url("http://dl.bintray.com/dancingrobot84/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("org.jetbrains" % "sbt-ide-settings" % "0.1.1")
addSbtPlugin("com.dancingrobot84" % "sbt-idea-plugin" % "0.4.2")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.2.11")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.7.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")

// FIXME coursier as a plugin currently breaks tests
//addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC3")
