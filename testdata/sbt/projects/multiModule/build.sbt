
lazy val foo = project.in(file("foo")).dependsOn(bar)

lazy val bar = project.in(file("bar"))

lazy val multiModule = project.in(file(".")).aggregate(foo, bar)