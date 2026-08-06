package org.jetbrains.plugins.scala.codeInspection.all

import com.intellij.codeInspection.LocalInspectionEP
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaVersion}
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, ScalaSdkOwner}

abstract class AllInspectionsTestBase extends ScalaLightCodeInsightFixtureTestCase {
  override protected def defaultVersionOverride: Option[ScalaVersion] = Some(ScalaSdkOwner.preferableSdkVersion)

  protected override def setUp(): Unit = {
    super.setUp()
    def acquireAllInspectionEPs(): Seq[LocalInspectionEP] =
      LocalInspectionEP.LOCAL_INSPECTION
        .getExtensions()
        .toSeq
    myFixture.enableInspections(acquireAllInspectionEPs().map(_.getInstance()): _*)
  }

  def checkHighlightingThrowsNoExceptions(code: String): Unit = {
    myFixture.configureByText(ScalaFileType.INSTANCE, code)
    myFixture.doHighlighting()
  }
}
