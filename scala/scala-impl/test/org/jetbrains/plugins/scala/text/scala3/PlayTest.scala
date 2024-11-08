package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase

class PlayTest extends TextToTextTestBase(
  Seq(
    "com.typesafe.play" %% "play" % "2.9.0-M4",
  ),
  Seq("controllers", "models", "play", "views"), Set.empty, 620,
  Set(
    "play.api.libs.crypto.CSRFTokenSigner", // No @deprecated annotation
    "play.api.mvc.ActionBuilder", // Extra [Nothing] type argument
    "play.api.mvc.DefaultActionBuilderImpl", // Extra [Nothing] type argument
    "play.api.mvc.DefaultMessagesActionBuilderImpl", // Extra [Nothing] type argument
    "play.api.mvc.MessagesRequest", // No _root_.play.api.mvc. qualifier
    "views.html.helper.form", // By-name function type parameter
    "views.html.helper.script", // By-name function type parameter
    "views.html.helper.style", // By-name function type parameter
  )
)