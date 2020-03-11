package org.jetbrains.plugins.scala.codeInsight
package intention

import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings
import com.intellij.codeInsight.intention.{IntentionActionBean, IntentionManager}
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.junit.Assert

import scala.collection.JavaConverters._
import scala.util.Try

class GeneralIntentionActionSanityTest extends SimpleTestCase {

  def acquireAllIntentionActionEPs(): Seq[IntentionActionBean] =
    IntentionManager.EP_INTENTION_ACTIONS
      .getExtensions()
      .toSeq

  def test_all_intention_actions_have_descriptions(): Unit = {
    val allMetaData = IntentionManagerSettings.getInstance().getMetaData.asScala


    val (exceptions, intentionActionsWithoutDescription) =
      allMetaData
        .flatMap { metaData =>
          Try(metaData.getDescription).failed.toOption.map(e => e -> metaData.getFamily)
        }
        .sortBy(_._2)
        .unzip

    exceptions.foreach(println)

    Assert.assertTrue(
      s"The following intention actions (${intentionActionsWithoutDescription.size}) do not have a description file:\n  ${intentionActionsWithoutDescription.mkString(",\n  ")}",
      intentionActionsWithoutDescription.isEmpty
    )
  }

  /*override def setUp(): Unit = {
    super.setUp()
    val intentionManagerSettings = IntentionManagerSettings.getInstance()
    acquireAllIntentionActionEPs()
      .foreach(intentionManagerSettings.reg)
  }*/
}
