package org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.EditorActionTestBase

abstract class Scala3EnterBaseTest extends EditorActionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  protected def doEnterTest(before: String, after: String, afterOther: String*): Unit = {
    (before +: after +: afterOther).sliding(2).foreach { case Seq(b, a) =>
      performTest(b, a, stripTrailingSpacesAfterAction = true) { () =>
        performEnterAction()
      }
    }
  }

  protected def doEnterTest_NonStrippingTrailingSpaces(before: String, after: String, afterOther: String*): Unit = {
    (before +: after +: afterOther).sliding(2).foreach { case Seq(b, a) =>
      performTest(b, a, stripTrailingSpacesAfterAction = false) { () =>
        performEnterAction()
      }
    }
  }
}
