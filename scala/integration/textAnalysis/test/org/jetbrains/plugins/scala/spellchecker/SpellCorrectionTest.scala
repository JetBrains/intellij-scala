package org.jetbrains.plugins.scala.spellchecker

class SpellCorrectionTest extends SpellCorrectionTestBase {

  def test_className(): Unit =
    doTest(s"class $NAME {}", "Testi")("Test")

  def test_objectName(): Unit =
    doTest(s"object $NAME {}", "Testi")("Test")

  def test_val(): Unit =
    doTest(s"object Obj { val $NAME = 0 }", "testi")("test")

  def test_def(): Unit =
    doTest(s"object Obj { def $NAME = 0 }", "testi")("test")

  def test_var(): Unit =
    doTest(s"object Obj { var $NAME = 0 }", "testi")("test")
}
