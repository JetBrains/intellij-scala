package org.jetbrains.plugins.scala.lang.formatter.scalafmt

trait UseConfig_2_7 extends ScalaFmtTestBase {

  override def setUp(): Unit = {
    super.setUp()

    setScalafmtConfig("empty_config_2_7_5.conf")
    scalaSettings.SCALAFMT_FALLBACK_TO_DEFAULT_SETTINGS = false
  }
}
