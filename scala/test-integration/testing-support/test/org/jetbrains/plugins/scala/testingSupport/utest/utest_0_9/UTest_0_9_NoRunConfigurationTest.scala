package org.jetbrains.plugins.scala.testingSupport.utest.utest_0_9

import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase

trait UTest_0_9_NoRunConfigurationTest extends UTestTestCase {

  protected val noRunConfigTestFileName = "NoRunConfigTest.scala"

  def testNoRunConfigurationOnClassNotInheritingTestSuite(): Unit = {
    addScalaSourceFileImmediately(noRunConfigTestFileName,
      s"""import utest._
         |
         |class MyImpostorClassNotInherits(param: Int) {
         |  def tests: Tests = Tests { test("test1") {}}
         |}
         |""".stripMargin
    )
    assertNoConfigurationCreatedInTheBeginningOfEachLineInFile(noRunConfigTestFileName)
  }

  def testNoRunConfigurationOnClassWithParamWithoutDefaultConstructor(): Unit = {
    addScalaSourceFileImmediately(noRunConfigTestFileName,
      s"""import utest._
         |
         |class MyImpostorClassWithParam(param: Int) extends TestSuite {
         |  override def tests: Tests = Tests { test("test1") {}}
         |}
         |""".stripMargin
    )
    assertNoConfigurationCreatedInTheBeginningOfEachLineInFile(noRunConfigTestFileName)
  }

  def testNoRunConfigurationOnAbstractClass(): Unit = {
    addScalaSourceFileImmediately(noRunConfigTestFileName,
      s"""import utest._
         |
         |abstract class MyImpostorClassAbstract extends TestSuite {
         |  override def tests: Tests = Tests { test("test1") {}}
         |}
         |""".stripMargin
    )
    assertNoConfigurationCreatedInTheBeginningOfEachLineInFile(noRunConfigTestFileName)
  }

  def testNoRunConfigurationOnLocalClassInObject(): Unit = {
    addScalaSourceFileImmediately(noRunConfigTestFileName,
      s"""import utest._
         |
         |object ObjectWrapper {
         |  class MyImpostorClassLocalInObject extends TestSuite {
         |    override def tests: Tests = Tests { test("test1") {}}
         |  }
         |}
         |""".stripMargin
    )
    assertNoConfigurationCreatedInTheBeginningOfEachLineInFile(noRunConfigTestFileName)
  }

  def testNoRunConfigurationOnLocalObjectInObject(): Unit = {
    addScalaSourceFileImmediately(noRunConfigTestFileName,
      s"""import utest._
         |
         |object ObjectWrapper {
         |  object MyImpostorObjectLocalInObject extends TestSuite {
         |    override def tests: Tests = Tests { test("test1") {}}
         |  }
         |}
         |""".stripMargin
    )
    assertNoConfigurationCreatedInTheBeginningOfEachLineInFile(noRunConfigTestFileName)
  }

  def testNoRunConfigurationOnLocalClassInClass(): Unit = {
    addScalaSourceFileImmediately(noRunConfigTestFileName,
      s"""import utest._
         |
         |class ObjectWrapper {
         |  class MyImpostorClassLocalInClass extends TestSuite {
         |    override def tests: Tests = Tests { test("test1") {}}
         |  }
         |}
         |""".stripMargin
    )
    assertNoConfigurationCreatedInTheBeginningOfEachLineInFile(noRunConfigTestFileName)
  }

  def testNoRunConfigurationOnLocalObjectInClass(): Unit = {
    addScalaSourceFileImmediately(noRunConfigTestFileName,
      s"""import utest._
         |
         |class ObjectWrapper {
         |  object MyImpostorObjectLocalInClass extends TestSuite {
         |    override def tests: Tests = Tests { test("test1") {}}
         |  }
         |}
         |""".stripMargin
    )
    assertNoConfigurationCreatedInTheBeginningOfEachLineInFile(noRunConfigTestFileName)
  }
}
