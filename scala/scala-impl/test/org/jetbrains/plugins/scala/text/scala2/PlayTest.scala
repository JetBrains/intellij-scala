package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class PlayTest extends TextToTextTestBase(
  Seq(
    "com.typesafe.play" %% "play" % "2.9.6",
  ),
  Seq("controllers", "models", "play", "views"), Set.empty, 605,
  Set(
    "controllers.AssetsModule", // _1
    "play.api.i18n.I18nModule", // I18nModule.this._1
    "views.html.helper.form", // By-name function type parameter
    "views.html.helper.script", // By-name function type parameter
    "views.html.helper.style", // By-name function type parameter
    "play.api.libs.json.DefaultReads", // Enum
    "play.api.libs.json.Json", // Enum
  ),
  includeScalaReflect = true
)