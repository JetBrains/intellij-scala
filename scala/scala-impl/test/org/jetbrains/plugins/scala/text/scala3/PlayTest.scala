package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class PlayTest extends TextToTextTestBase(
  dependencies = Seq(
    "com.typesafe.play" %% "play" % "2.9.6",
  ),
  packages = Seq("controllers", "models", "play", "views"),
  minClassCount = 628,
  classExceptions = Set(
    "play.api.mvc.ActionBuilder", // Extra [Nothing] type argument
    "play.api.mvc.DefaultActionBuilderImpl", // Extra [Nothing] type argument
    "play.api.mvc.DefaultMessagesActionBuilderImpl", // Extra [Nothing] type argument
    "views.html.helper.form", // By-name function type parameter
    "views.html.helper.script", // By-name function type parameter
    "views.html.helper.style", // By-name function type parameter
  )
)