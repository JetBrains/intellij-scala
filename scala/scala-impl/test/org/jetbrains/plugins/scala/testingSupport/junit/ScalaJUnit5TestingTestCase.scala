package org.jetbrains.plugins.scala.testingSupport.junit

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

class ScalaJUnit5TestingTestCase extends ScalaJUnitTestingTestCaseBase {

  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("org.junit.jupiter" % "junit-jupiter" % "5.8.1").transitive())
  )

  //SCL-19710
  private val ScalaJUnit5_ParametrizedTests_FileName = "ScalaJUnit5_ParametrizedTests_FileName.scala"
  addSourceFile(ScalaJUnit5_ParametrizedTests_FileName,
    """import org.junit.jupiter.api.TestInstance.Lifecycle
      |import org.junit.jupiter.api._
      |import org.junit.jupiter.params.ParameterizedTest
      |import org.junit.jupiter.params.provider.MethodSource
      |
      |import java.io.IOException
      |
      |@TestInstance(Lifecycle.PER_CLASS)
      |@TestMethodOrder(classOf[MethodOrderer.OrderAnnotation])
      |class ScalaJUnit5_ParametrizedTests {
      |
      |  @ParameterizedTest
      |  @MethodSource(Array("rulesData"))
      |  @Order(10)
      |  def Test_10_Rules(configValue: String, rules: String): Unit = {}
      |
      |  @ParameterizedTest
      |  @MethodSource(Array("rulesData"))
      |  @Order(51)
      |  def Test_5_1_Rules(configValue: String, rules: String): Unit = {}
      |
      |  @BeforeAll
      |  @throws[IOException]
      |  def beforeAll(): Unit = {}
      |
      |  @BeforeEach
      |  @throws[IOException]
      |  def beforeEach(): Unit = {}
      |
      |  def rulesData: Array[Array[String]] = {
      |    Array(
      |      Array("cv1", "rules1"),
      |      Array("cv2", "rules2"),
      |    )
      |  }
      |}
      |""".stripMargin.trim()
  )

  def testParametrizedTests_WholeSuite(): Unit = {
    runTestByLocation(loc(ScalaJUnit5_ParametrizedTests_FileName, 9, 10),
      assertIsJUnitClassConfiguration(_, "ScalaJUnit5_ParametrizedTests"),
      assertJUnitTestTree(_, MyTestTreeNode(null, "[root]", List(
        MyTestTreeNode("ScalaJUnit5_ParametrizedTests", List(
          MyTestTreeNode("Test_10_Rules(String, String)", List(
            MyTestTreeNode("[1] configValue=cv1, rules=rules1"),
            MyTestTreeNode("[2] configValue=cv2, rules=rules2")
          )),
          MyTestTreeNode("Test_5_1_Rules(String, String)", List(
            MyTestTreeNode("[1] configValue=cv1, rules=rules1"),
            MyTestTreeNode("[2] configValue=cv2, rules=rules2")
          ))
        ))
      )))
    )
  }

  def testParametrizedTests_SingleTest1(): Unit = {
    runTestByLocation(loc(ScalaJUnit5_ParametrizedTests_FileName, 14, 10),
      assertIsJUnitTestMethodConfiguration(_, "ScalaJUnit5_ParametrizedTests", "Test_10_Rules"),
      assertJUnitTestTree(_, MyTestTreeNode(null, "[root]", List(
        MyTestTreeNode("ScalaJUnit5_ParametrizedTests", List(
          MyTestTreeNode("Test_10_Rules(String, String)", List(
            MyTestTreeNode("[1] configValue=cv1, rules=rules1"),
            MyTestTreeNode("[2] configValue=cv2, rules=rules2")
          ))
        ))
      )))
    )
  }

  def testParametrizedTests_SingleTest2(): Unit = {
    runTestByLocation(loc(ScalaJUnit5_ParametrizedTests_FileName, 18, 10),
      assertIsJUnitTestMethodConfiguration(_, "ScalaJUnit5_ParametrizedTests", "Test_5_1_Rules"),
      assertJUnitTestTree(_, MyTestTreeNode(null, "[root]", List(
        MyTestTreeNode("ScalaJUnit5_ParametrizedTests", List(
          MyTestTreeNode("Test_5_1_Rules(String, String)", List(
            MyTestTreeNode("[1] configValue=cv1, rules=rules1"),
            MyTestTreeNode("[2] configValue=cv2, rules=rules2")
          ))
        ))
      )))
    )
  }
}
