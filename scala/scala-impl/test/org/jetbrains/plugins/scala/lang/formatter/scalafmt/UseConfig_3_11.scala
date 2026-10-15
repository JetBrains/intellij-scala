package org.jetbrains.plugins.scala.lang.formatter.scalafmt

trait UseConfig_3_11 extends ScalaFmtTestBase {

  override def setUp(): Unit = {
    super.setUp()

    setScalafmtConfig("empty_config_3_11_5.conf")
    scalaSettings.SCALAFMT_FALLBACK_TO_DEFAULT_SETTINGS = false
  }
}
