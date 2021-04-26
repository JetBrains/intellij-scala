package org.jetbrains.plugins.scala.lang.actions.editor

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.EditorActionTestBase

abstract class Scala3EditorActionTestBase extends EditorActionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0
}
