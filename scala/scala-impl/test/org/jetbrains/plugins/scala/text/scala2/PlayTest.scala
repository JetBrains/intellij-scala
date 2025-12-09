package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class PlayTest extends TextToTextTestBase(
  dependencies = Seq(
    "com.typesafe.play" %% "play" % "2.9.6",
  ),
  packages = Seq("controllers", "models", "play", "views"),
  minClassCount = 605,
  classExceptions = Set(
    "views.html.helper.form", // By-name function type parameter
    "views.html.helper.script", // By-name function type parameter
    "views.html.helper.style", // By-name function type parameter
    "play.api.libs.json.DefaultReads", // Enum
    "play.api.libs.json.Json", // Enum
  ),
  includeScalaReflect = true
)