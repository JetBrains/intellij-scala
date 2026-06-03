package org.jetbrains.plugins.scala.lang.refactoring

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3StatementMoverTest extends ScalaStatementMoverTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testBracelessOneMethodExtensionMoveUp(): Unit = {
    val fileText =
      s"""object Scope {
         |  class MyClass
         |  opaque type MyOpaqueType = String
         |
         |  extension (t: MyClass)
         |    def myExtension1: String = ""
         |    def myExtension2: String = ""
         |    def myExtension3: String = ""
         |
         |  exten${|}sion (t: MyOpaqueType)
         |    def myExtension11: String = ""
         |}""".stripMargin

    val expectedResult =
      """object Scope {
        |  class MyClass
        |  opaque type MyOpaqueType = String
        |
        |  extension (t: MyOpaqueType)
        |    def myExtension11: String = ""
        |
        |  extension (t: MyClass)
        |    def myExtension1: String = ""
        |    def myExtension2: String = ""
        |    def myExtension3: String = ""
        |}""".stripMargin

    fileText.movedUpIs(expectedResult)
  }

  def testBracelessMultipleMethodsExtensionMoveDown(): Unit = {
    val fileText =
      s"""object Scope {
         |  class MyClass
         |  opaque type MyOpaqueType = String
         |
         |  ${|}extension (t: MyClass)
         |    def myExtension1: String = ""
         |    def myExtension2: String = ""
         |    def myExtension3: String = ""
         |
         |  extension (t: MyOpaqueType)
         |    def myExtension11: String = ""
         |}""".stripMargin

    val expectedResult =
      """object Scope {
        |  class MyClass
        |  opaque type MyOpaqueType = String
        |
        |  extension (t: MyOpaqueType)
        |    def myExtension11: String = ""
        |
        |  extension (t: MyClass)
        |    def myExtension1: String = ""
        |    def myExtension2: String = ""
        |    def myExtension3: String = ""
        |}""".stripMargin

    fileText.movedDownIs(expectedResult)
  }

  def testBracelessOneMethodExtensionMoveDown(): Unit = {
    val fileText =
      s"""object Scope {
         |  class MyClass
         |  opaque type MyOpaqueType = String
         |
         |  exten${|}sion (t: MyOpaqueType)
         |    def myExtension11: String = ""
         |
         |  extension (t: MyClass)
         |    def myExtension1: String = ""
         |    def myExtension2: String = ""
         |    def myExtension3: String = ""
         |
         |}""".stripMargin

    val expectedResult =
      """object Scope {
        |  class MyClass
        |  opaque type MyOpaqueType = String
        |
        |  extension (t: MyClass)
        |    def myExtension1: String = ""
        |    def myExtension2: String = ""
        |    def myExtension3: String = ""
        |
        |  extension (t: MyOpaqueType)
        |    def myExtension11: String = ""
        |
        |}""".stripMargin

    fileText.movedDownIs(expectedResult)
  }

  def testBracelessMultipleMethodsExtensionMoveUp(): Unit = {
    val fileText =
      s"""object Scope {
         |  class MyClass
         |  opaque type MyOpaqueType = String
         |
         |  extension (t: MyOpaqueType)
         |    def myExtension11: String = ""
         |
         |  ${|}extension (t: MyClass)
         |    def myExtension1: String = ""
         |    def myExtension2: String = ""
         |    def myExtension3: String = ""
         |}""".stripMargin

    val expectedResult =
      """object Scope {
        |  class MyClass
        |  opaque type MyOpaqueType = String
        |
        |  extension (t: MyClass)
        |    def myExtension1: String = ""
        |    def myExtension2: String = ""
        |    def myExtension3: String = ""
        |
        |  extension (t: MyOpaqueType)
        |    def myExtension11: String = ""
        |}""".stripMargin

    fileText.movedUpIs(expectedResult)
  }

  def testBracelessOneMethodExtensionMoveUpOutsideBlock(): Unit = {
    val fileText =
      s"""object Scope {
         |
         |  class MyClass
         |  opaque type MyOpaqueType = String
         |  locally {
         |    ${|}extension (t: MyOpaqueType)
         |      def myExtension11: String = ""
         |
         |    extension (t: MyClass)
         |      def myExtension1: String = ""
         |      def myExtension2: String = ""
         |      def myExtension3: String = ""
         |  }
         |}""".stripMargin

    val expectedResult =
      """object Scope {
        |
        |  class MyClass
        |  opaque type MyOpaqueType = String
        |  extension (t: MyOpaqueType)
        |    def myExtension11: String = ""
        |  locally {
        |
        |    extension (t: MyClass)
        |      def myExtension1: String = ""
        |      def myExtension2: String = ""
        |      def myExtension3: String = ""
        |  }
        |}""".stripMargin

    fileText.movedUpIs(expectedResult)
  }

  def testBracelessOneMethodExtensionMoveDownInsideBlock(): Unit = {
    val fileText =
      s"""object Scope {
         |
         |  class MyClass
         |  opaque type MyOpaqueType = String
         |  ${|}extension (t: MyOpaqueType)
         |    def myExtension11: String = ""
         |  locally {
         |
         |    extension (t: MyClass)
         |      def myExtension1: String = ""
         |      def myExtension2: String = ""
         |      def myExtension3: String = ""
         |  }
         |}""".stripMargin

    val expectedResult =
      """object Scope {
        |
        |  class MyClass
        |  opaque type MyOpaqueType = String
        |  locally {
        |    extension (t: MyOpaqueType)
        |      def myExtension11: String = ""
        |
        |    extension (t: MyClass)
        |      def myExtension1: String = ""
        |      def myExtension2: String = ""
        |      def myExtension3: String = ""
        |  }
        |}""".stripMargin

    fileText.movedDownIs(expectedResult)
  }
}
