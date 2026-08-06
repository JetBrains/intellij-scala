package org.jetbrains.plugins.scala.testingSupport.scalatest.scalatest_3_2.scala_2_13

import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.util.JDOMUtil
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestPackageTest
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.{ScalaTestConfigurationType, ScalaTestRunConfiguration}
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{AllInPackageTestData, RegexpTestData}
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, TestKind}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}

import java.{util => ju}

class Scalatest_3_2_Scala_2_13_PackageTest extends Scalatest_3_2_Scala_2_13_Base
  with ScalaTestPackageTest {

  def testAllInPackageRunConfigurationSerializationDoesNotPersistClassBuf(): Unit = {
    val settings = createTestFromLocation(packageLoc(packageName1))
    val configuration = settings.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
    val testData = configuration.testConfigurationData.asInstanceOf[AllInPackageTestData]

    val testMap = testData.getTestMap
    assertTrue(testMap.contains(s"$packageName1.Test1"))
    assertTrue(testMap.contains(s"$packageName1.Test2"))
    assertFalse("The test must populate classBuf in memory to reproduce the serialization leak", testData.classBuf.isEmpty)

    val xml = writeScheme(settings.asInstanceOf[RunnerAndConfigurationSettingsImpl])
    assertContains(xml, "name=\"testKind\"")
    assertContains(xml, "value=\"All in package\"")
    assertContains(xml, "name=\"testPackagePath\"")
    assertContains(xml, s"value=\"$packageName1\"")
    assertDoesNotContain(xml, "classBuf")
    assertDoesNotContain(xml, s"$packageName1.Test1")
    assertDoesNotContain(xml, s"$packageName1.Test2")
  }

  def testRegexpRunConfigurationSerializationDoesNotPersistTestsBuf(): Unit = {
    val configuration = createRegexpConfiguration(
      classRegexps = Array(s"$packageName1\\.Test.*"),
      testRegexps = Array("Test1|Test2")
    )
    val settings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(getProject), configuration)
    val testData = configuration.testConfigurationData.asInstanceOf[RegexpTestData]

    testData.testsBuf.put(s"$packageName1.Test1", new ju.HashSet[String](ju.List.of("Test1")))
    testData.testsBuf.put(s"$packageName1.Test2", new ju.HashSet[String](ju.List.of("Test2")))
    assertFalse("The test must populate testsBuf in memory to reproduce the serialization leak", testData.testsBuf.isEmpty)

    val xml = writeScheme(settings)
    assertContains(xml, "name=\"testKind\"")
    assertContains(xml, "value=\"Regular expression\"")
    assertContains(xml, "name=\"classRegexps\"")
    assertContains(xml, s"value=\"$packageName1\\.Test.*\"")
    assertContains(xml, "name=\"testRegexps\"")
    assertContains(xml, "value=\"Test1|Test2\"")
    assertDoesNotContain(xml, "testsBuf")
    assertDoesNotContain(xml, s"$packageName1.Test1")
    assertDoesNotContain(xml, s"$packageName1.Test2")
  }

  def testRunConfigurationReadExternalIgnoresPersistedRuntimeCaches(): Unit = {
    val classBufXml =
      s"""<configuration>
         |  <option name="testKind" value="All in package" />
         |  <option name="testPackagePath" value="$packageName1" />
         |  <option name="classBuf">
         |    <list>
         |      <option value="$packageName1.Test1" />
         |    </list>
         |  </option>
         |</configuration>""".stripMargin
    val allInPackageConfiguration = createRunConfiguration()
    allInPackageConfiguration.readExternal(JDOMUtil.load(classBufXml))
    val allInPackageData = allInPackageConfiguration.testConfigurationData.asInstanceOf[AllInPackageTestData]
    assertEquals(packageName1, allInPackageData.testPackagePath)
    assertTrue("Old persisted classBuf must be ignored", allInPackageData.classBuf.isEmpty)

    val testsBufXml =
      s"""<configuration>
         |  <option name="testKind" value="Regular expression" />
         |  <option name="classRegexps">
         |    <array>
         |      <option value="$packageName1\\.Test.*" />
         |    </array>
         |  </option>
         |  <option name="testRegexps">
         |    <array>
         |      <option value="Test1|Test2" />
         |    </array>
         |  </option>
         |  <option name="testsBuf">
         |    <map>
         |      <entry key="$packageName1.Test1">
         |        <value>
         |          <set>
         |            <option value="Test1" />
         |          </set>
         |        </value>
         |      </entry>
         |    </map>
         |  </option>
         |</configuration>""".stripMargin
    val regexpConfiguration = createRunConfiguration()
    regexpConfiguration.readExternal(JDOMUtil.load(testsBufXml))
    val regexpData = regexpConfiguration.testConfigurationData.asInstanceOf[RegexpTestData]
    assertEquals(Seq(s"$packageName1\\.Test.*"), regexpData.classRegexps.toSeq)
    assertEquals(Seq("Test1|Test2"), regexpData.testRegexps.toSeq)
    assertTrue("Old persisted testsBuf must be ignored", regexpData.testsBuf.isEmpty)
  }

  private def createRegexpConfiguration(classRegexps: Array[String], testRegexps: Array[String]): ScalaTestRunConfiguration = {
    val configuration = createRunConfiguration()
    configuration.setModule(getModule)
    configuration.testKind = TestKind.REGEXP
    configuration.testConfigurationData = RegexpTestData(configuration, classRegexps, testRegexps)
    configuration
  }

  private def createRunConfiguration(): ScalaTestRunConfiguration =
    new ScalaTestRunConfiguration(getProject, ScalaTestConfigurationType().confFactory, "test-conf-name")

  private def writeScheme(settings: RunnerAndConfigurationSettingsImpl): String =
    JDOMUtil.write(settings.writeScheme(), "\n")

  private def assertContains(text: String, expectedSubstring: String): Unit =
    assertTrue(s"Expected text to contain '$expectedSubstring':\n$text", text.contains(expectedSubstring))

  private def assertDoesNotContain(text: String, unexpectedSubstring: String): Unit =
    assertFalse(s"Expected text not to contain '$unexpectedSubstring':\n$text", text.contains(unexpectedSubstring))
}
