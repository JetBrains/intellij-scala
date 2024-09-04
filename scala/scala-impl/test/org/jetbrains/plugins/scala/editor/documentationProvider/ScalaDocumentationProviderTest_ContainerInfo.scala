package org.jetbrains.plugins.scala.editor.documentationProvider

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.editor.documentationProvider.util.ScalaDocumentationsContainerInfoSectionTesting

final class ScalaDocumentationProviderTest_ContainerInfo extends ScalaDocumentationProviderTestBase
  with ScalaDocumentationsContainerInfoSectionTesting {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  //
  // Top level definitions of all kind
  // Check that the package icon is applied to all kinds of definitions
  //
  private val TopLevelContainerInfoCommon: String =
  """<icon src="AllIcons.Nodes.Package"/>&nbsp;<a href="psi_element://org.example"><code>org.example</code></a>"""

  private def doTestTopLevel(definitionText: String): Unit =
    doGenerateDocContainerInfoTest(
      s"""package org.example
         |$definitionText
         |""".stripMargin,
      TopLevelContainerInfoCommon
    )

  def testTopLevel_Class(): Unit = doTestTopLevel(s"class ${|}MyClass")

  def testTopLevel_Trait(): Unit = doTestTopLevel(s"trait ${|}MyTrait")

  def testTopLevel_Object(): Unit = doTestTopLevel(s"object ${|}MyObject")

  def testTopLevel_Enum(): Unit = doTestTopLevel(
    s"""enum ${|}MyEnum {
       |  case Red, Green, Blue
       |}""".stripMargin
  )

  def testTopLevel_Def(): Unit = doTestTopLevel(s"def ${|}myFunction(x: Int): Int = x * x")

  def testTopLevel_Val(): Unit = doTestTopLevel(s"val ${|}myVal: Int = 42")

  def testTypeTopLevel_Alias(): Unit = doTestTopLevel(s"type ${|}MyTypeAlias = String")

  def testTopLevel_Given(): Unit = doTestTopLevel(s"given ${|}MyGiven as MyTrait")

  def testTopLevel_Extension(): Unit =
    doTestTopLevel(
      s"""extension (s: String)
         |  def ${|}greet: String =???"""".stripMargin
    )

  //
  // All kinds of definitions in object
  // Check that a non-package icon is applied to all kinds of definitions within an object
  //
  private val ObjectContainerInfoCommon: String =
    """<icon src="org.jetbrains.plugins.scala.icons.Icons.OBJECT"/>&nbsp;<a href="psi_element://org.example.MyObject"><code>org.example.MyObject</code></a>"""

  private def doTestInObject(definitionText: String): Unit =
    doGenerateDocContainerInfoTest(
      s"""package org.example
         |object MyObject {
         |  $definitionText
         |}
         |""".stripMargin,
      ObjectContainerInfoCommon
    )

  def testInObject_Class(): Unit = doTestInObject(s"class ${|}MyClass")

  def testInObject_Trait(): Unit = doTestInObject(s"trait ${|}MyTrait")

  def testInObject_Enum(): Unit = doTestInObject(
    s"""enum ${|}MyEnum {
       |  case Red, Green, Blue
       |}""".stripMargin
  )

  def testInObject_Def(): Unit = doTestInObject(s"def ${|}myFunction(x: Int): Int = x * x")

  def testInObject_Val(): Unit = doTestInObject(s"val ${|}myVal: Int = 42")

  def testInObject_TypeAlias(): Unit = doTestInObject(s"type ${|}MyTypeAlias = String")

  def testInObject_Given(): Unit = doTestInObject(s"given ${|}MyGiven as MyTrait")

  def testInObject_Extension(): Unit = doTestInObject(
    s"""extension (s: String)
       |  def ${|}greet: String =???"""".stripMargin
  )

  //
  // Test single definition kind in different container definitions
  //
  def testContainerIcon_Package(): Unit = doGenerateDocContainerInfoTest(
    s"""package org.example
       |
       |class ${|}Dummy
       |""".stripMargin,
    """<icon src="AllIcons.Nodes.Package"/>&nbsp;<a href="psi_element://org.example"><code>org.example</code></a>"""
  )

  def testContainerIcon_Class(): Unit = doGenerateDocContainerInfoTest(
    s"""package org.example
       |
       |class Container {
       |  class ${|}Dummy
       |}
       |""".stripMargin,
    """<icon src="org.jetbrains.plugins.scala.icons.Icons.CLASS"/>&nbsp;<a href="psi_element://org.example.Container"><code>org.example.Container</code></a>"""
  )

  def testContainerIcon_Object(): Unit = doGenerateDocContainerInfoTest(
    s"""package org.example
       |
       |object Container {
       |  class ${|}Dummy
       |}
       |""".stripMargin,
    """<icon src="org.jetbrains.plugins.scala.icons.Icons.OBJECT"/>&nbsp;<a href="psi_element://org.example.Container"><code>org.example.Container</code></a>"""
  )

  def testContainerIcon_Trait(): Unit = doGenerateDocContainerInfoTest(
    s"""package org.example
       |
       |trait Container {
       |  class ${|}Dummy
       |}
       |""".stripMargin,
    """<icon src="org.jetbrains.plugins.scala.icons.Icons.TRAIT"/>&nbsp;<a href="psi_element://org.example.Container"><code>org.example.Container</code></a>"""
  )

  def testContainerIcon_Enum_SingletonEnumCase(): Unit = doGenerateDocContainerInfoTest(
    s"""package org.example
       |
       |enum Container {
       |  case ${|}DummyCase
       |}
       |""".stripMargin,
    """<icon src="org.jetbrains.plugins.scala.icons.Icons.ENUM"/>&nbsp;<a href="psi_element://org.example.Container"><code>org.example.Container</code></a>"""
  )
  def testContainerIcon_Enum_CaseClassEnumCase(): Unit = doGenerateDocContainerInfoTest(
    s"""package org.example
       |
       |enum Container {
       |  case ${|}DummyCase(x: Int)
       |}
       |""".stripMargin,
    """<icon src="org.jetbrains.plugins.scala.icons.Icons.ENUM"/>&nbsp;<a href="psi_element://org.example.Container"><code>org.example.Container</code></a>"""
  )

  def testContainerIcon_PackageObject(): Unit = doGenerateDocContainerInfoTest(
    s"""package org.example
       |
       |package object Container {
       |  class ${|}Dummy
       |}
       |""".stripMargin,
    """<icon src="org.jetbrains.plugins.scala.icons.Icons.PACKAGE_OBJECT"/>&nbsp;<a href="psi_element://org.example.Container"><code>org.example.Container</code></a>"""
  )
}