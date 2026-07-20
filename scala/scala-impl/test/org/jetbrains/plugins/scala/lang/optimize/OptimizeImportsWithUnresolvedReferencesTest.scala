package org.jetbrains.plugins.scala.lang.optimize

import com.intellij.idea.TestFor
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
final class OptimizeImportsWithUnresolvedReferencesTest extends OptimizeImportsTestBase {
  @Test
  @TestFor(issues = Array("SCL-21808"))
  def preservePotentiallyUsedUnresolvedImports(): Unit = {
    myFixture.addFileToProject("org/example/package.scala", "package org.example")
    doTest(
      """import org.example.Referenced
        |import org.example.Unreferenced
        |import org.example.{Original => Renamed}
        |
        |object Usage {
        |  val referenced: Referenced = ???
        |  val renamed: Renamed = ???
        |}""".stripMargin,
      s"""import org.example.{Referenced, Original => Renamed}
         |
         |object Usage {
         |  val referenced: Referenced = ???
         |  val renamed: Renamed = ???
         |}""".stripMargin,
      "Removed 1 import"
    )
  }

  @Test
  @TestFor(issues = Array("SCL-21808"))
  def preservePotentiallyUsedUnresolvedLocalImports(): Unit = {
    myFixture.addFileToProject("org/example/package.scala", "package org.example")
    doTest(
      """object Usage {
        |  import org.example.Referenced
        |  import org.example.Unreferenced
        |  import org.example.{Original => Renamed}
        |
        |  val referenced: Referenced = ???
        |  val renamed: Renamed = ???
        |}""".stripMargin,
      s"""object Usage {
         |  import org.example.{Referenced, Original => Renamed}
         |
         |  val referenced: Referenced = ???
         |  val renamed: Renamed = ???
         |}""".stripMargin,
      "Removed 1 import"
    )
  }

  @Test
  @TestFor(issues = Array("SCL-21808"))
  def preservePotentiallyUsedUnresolvedLocalImportsBeforeUsage(): Unit = {
    myFixture.addFileToProject("org/example/package.scala", "package org.example")
    doTest(
      """object Usage {
        |  val referenced: Referenced = ???
        |
        |  import org.example.Referenced
        |  import org.example.Unreferenced
        |  import org.example.{Original => Renamed}
        |
        |  val renamed: Renamed = ???
        |}""".stripMargin,
      s"""object Usage {
         |  val referenced: Referenced = ???
         |
         |  import org.example.{Original => Renamed}
         |
         |  val renamed: Renamed = ???
         |}""".stripMargin,
      "Removed 2 imports"
    )
  }

  @Test
  @TestFor(issues = Array("SCL-21808"))
  def respectLocalImportScope(): Unit = {
    myFixture.addFileToProject("org/example/package.scala", "package org.example")
    doTest(
      """object Usage {
        |  val before: BeforeImport = ???
        |
        |  locally {
        |    import org.example.BeforeImport
        |    ()
        |  }
        |}""".stripMargin,
      """object Usage {
        |  val before: BeforeImport = ???
        |
        |  locally {
        |    ()
        |  }
        |}""".stripMargin,
      "Removed 1 import"
    )
  }

  @Test
  @TestFor(issues = Array("SCL-21808"))
  def preservePotentiallyUsedSelectorAmongMultiple(): Unit = {
    myFixture.addFileToProject("org/example/package.scala", "package org.example")
    doTest(
      """import org.example.{Referenced, Unreferenced}
        |
        |object Usage {
        |  val referenced: Referenced = ???
        |}""".stripMargin,
      """import org.example.Referenced
        |
        |object Usage {
        |  val referenced: Referenced = ???
        |}""".stripMargin,
      "Removed 1 import"
    )
  }

  @Test
  @TestFor(issues = Array("SCL-21808"))
  def preservePotentiallyUsedUnresolvedImportInNestedScope(): Unit = {
    myFixture.addFileToProject("org/example/package.scala", "package org.example")
    doTest(
      """object Usage {
        |  import org.example.Foo
        |
        |  def test(): Unit = {
        |    val foo: Foo = ???
        |  }
        |}""".stripMargin
    )
  }

  @Test
  @TestFor(issues = Array("SCL-21808"))
  def wildcardImportIsNotPreservedForUnresolvedReference(): Unit = {
    myFixture.addFileToProject("org/example/package.scala", "package org.example")
    doTest(
      """import org.example._
        |
        |object Usage {
        |  val foo: Foo = ???
        |}""".stripMargin,
      """
        |
        |object Usage {
        |  val foo: Foo = ???
        |}""".stripMargin,
      "Removed 1 import"
    )
  }

  @Test
  @TestFor(issues = Array("SCL-21808"))
  def respectImportScopeInSiblingObject(): Unit = {
    myFixture.addFileToProject("org/example/package.scala", "package org.example")
    doTest(
      """object A {
        |  import org.example.Foo
        |
        |  val a = 1
        |}
        |
        |object B {
        |  val foo: Foo = ???
        |}""".stripMargin,
      """object A {
        |
        |  val a = 1
        |}
        |
        |object B {
        |  val foo: Foo = ???
        |}""".stripMargin,
      "Removed 1 import"
    )
  }
}
