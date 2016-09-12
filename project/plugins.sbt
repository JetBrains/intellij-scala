resolvers += Resolver.url("jetbrains-bintray",
  url("http://dl.bintray.com/jetbrains/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("org.jetbrains" % "sbt-ide-settings" % "0.1.1")

resolvers += Resolver.url("dancingrobot84-bintray",
  url("http://dl.bintray.com/dancingrobot84/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.dancingrobot84" % "sbt-idea-plugin" % "0.3.1")

addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.2.11")