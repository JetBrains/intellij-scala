package org.jetbrains.plugins.scala.lang.optimize.generated

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.optimize.OptimizeImportsTestBase

abstract class OptimizeImportsWildcardTestBase extends OptimizeImportsTestBase {

  override def folderPath: String = super.folderPath + "wildcard/"

  def testMayReplace(): Unit = doTest()

  def testNotUsedNameClash(): Unit = doTest()

  def testUsedNameClash(): Unit = doTest()

  def testNameClash(): Unit = doTest()

  def testImplicitClass(): Unit = doTest()

  def testImplicitDef(): Unit = doTest()

  def testNameConflictTypeAlias(): Unit = doTest()

  def testMergeIntoWildcard(): Unit = doTest()

  def testShadowAndSelectors(): Unit = doTest(
    """object A {
      |  class X
      |  class Y
      |
      |  implicit val s: String = ""
      |}
      |
      |object B1 {
      |  import A.{X => Z, s => _}
      |  new Z
      |}
      |
      |object B2 {
      |  import A.{Y, X => Z, s => _, _}
      |  (new Y, new Z)
      |}
      |
      |object B3 {
      |  import A.{X => _, _}
      |  new Y
      |}
      |
      |object B4 {
      |  import A.{s => implicitString, X => Z, _}
      |  (new Y, new Z)
      |}
      |
      |object B5 {
      |  import A.{s => implicitString, X => Z, _}
      |
      |  def foo(implicit s: String) = s
      |  foo
      |
      |  new Y
      |}
      |
      |object B6 {
      |  import A.{Y, X => Z, s => _, _}
      |  (new Y, new Z)
      |}
      |""".stripMargin,
    """object A {
      |  class X
      |  class Y
      |
      |  implicit val s: String = ""
      |}
      |
      |object B1 {
      |  import A.{X => Z, s => _}
      |  new Z
      |}
      |
      |object B2 {
      |  import A.{Y, X => Z, s => _}
      |  (new Y, new Z)
      |}
      |
      |object B3 {
      |  import A.{X => _, _}
      |  new Y
      |}
      |
      |object B4 {
      |  import A.{X => Z, _}
      |  (new Y, new Z)
      |}
      |
      |object B5 {
      |  import A.{s => implicitString, _}
      |
      |  def foo(implicit s: String) = s
      |  foo
      |
      |  new Y
      |}
      |
      |object B6 {
      |  import A.{Y, X => Z, s => _}
      |  (new Y, new Z)
      |}
      |""".stripMargin,
    "Removed 4 imports"
  )
}

abstract class OptimizeImportsWildcardCommon_212_213_Test extends OptimizeImportsWildcardTestBase {

  protected def addCommonDeclarationsWithNameClashes(): Unit = {
    myFixture.addFileToProject("org/example/declaration/all.scala",
      """package org.example.declaration.data
        |
        |class Random    // clashes with scala.util.Random
        |class Option[T] // clashes with scala.Option
        |class X
        |""".stripMargin
    )
  }

  protected val CodeBefore_ClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage =
    """package org.example.declaration.data
      |
      |import org.example.declaration.data.Random
      |import org.example.declaration.data.Option
      |
      |import scala.util._
      |
      |//noinspection TypeAnnotation
      |object UsageSameTargetPackage1 {
      |
      |  def main(args: Array[String]) = {
      |    println(this.getClass)
      |    println(classOf[Random])
      |    println(classOf[Option[_]])
      |    println(Properties.versionString)
      |    println()
      |  }
      |}""".stripMargin
  def testClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage(): Unit

  protected val CodeBefore_ClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_1 =
    """package org.example.declaration.data
      |
      |import org.example.declaration.data.{Random, Option}
      |
      |import scala.util._
      |
      |//noinspection TypeAnnotation
      |object UsageSameTargetPackage1 {
      |
      |  def main(args: Array[String]) = {
      |    println(this.getClass)
      |    println(classOf[Random])
      |    println(classOf[Option[_]])
      |    println(Properties.versionString)
      |    println()
      |  }
      |}""".stripMargin
  def testClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_1(): Unit

  protected val CodeBefore_ClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_2 =
    """package org.example.declaration.data
      |
      |import org.example.declaration.data.{Random, Option, _}
      |
      |import scala.util._
      |
      |//noinspection TypeAnnotation
      |object UsageSameTargetPackage1 {
      |
      |  def main(args: Array[String]) = {
      |    println(this.getClass)
      |    println(classOf[Random])
      |    println(classOf[Option[_]])
      |    println(Properties.versionString)
      |    println()
      |  }
      |}""".stripMargin
  def testClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_2(): Unit

  protected val CodeBefore_ClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_LocalImports =
    """package org.example.declaration.data
      |
      |import scala.util._
      |
      |//noinspection TypeAnnotation
      |object UsageSameTargetPackage1 {
      |
      |  def main(args: Array[String]) = {
      |    import org.example.declaration.data.Random
      |    import org.example.declaration.data.Option
      |
      |    println(this.getClass)
      |    println(classOf[Random])
      |    println(classOf[Option[_]])
      |    println(Properties.versionString)
      |    println()
      |  }
      |}""".stripMargin
  def testClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_LocalImports(): Unit = {
    addCommonDeclarationsWithNameClashes()
    doTest(
      CodeBefore_ClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_LocalImports,
      """package org.example.declaration.data
        |
        |import scala.util._
        |
        |//noinspection TypeAnnotation
        |object UsageSameTargetPackage1 {
        |
        |  def main(args: Array[String]) = {
        |    import org.example.declaration.data.Random
        |
        |    println(this.getClass)
        |    println(classOf[Random])
        |    println(classOf[Option[_]])
        |    println(Properties.versionString)
        |    println()
        |  }
        |}""".stripMargin,
      "Removed 1 import"
    )
  }

  protected val CodeBefore_ClassName_NoNameClashes_LocalImports =
    """package org.example.declaration.data
      |
      |//noinspection TypeAnnotation
      |object UsageSameTargetPackage1 {
      |
      |  def main(args: Array[String]) = {
      |    import org.example.declaration.data.Random
      |    import org.example.declaration.data.Option
      |
      |    println(this.getClass)
      |    println(classOf[Random])
      |    println(classOf[Option[_]])
      |    println(Properties.versionString)
      |    println()
      |  }
      |}""".stripMargin
  def testClassName_NoNameClashes_LocalImports(): Unit = {
    addCommonDeclarationsWithNameClashes()
    doTest(
      CodeBefore_ClassName_NoNameClashes_LocalImports,
      """package org.example.declaration.data
        |
        |//noinspection TypeAnnotation
        |object UsageSameTargetPackage1 {
        |
        |  def main(args: Array[String]) = {
        |
        |    println(this.getClass)
        |    println(classOf[Random])
        |    println(classOf[Option[_]])
        |    println(Properties.versionString)
        |    println()
        |  }
        |}""".stripMargin,
      "Removed 2 imports"
    )
  }

  //SCL-16599
  def testSameNameInDifferentPackages(): Unit = {
    myFixture.addFileToProject("foo/DataHolder.scala",
      """package foo
        |
        |case class DataHolder(data: String)
        |
        |class OtherClassA {}
        |
        |class OtherClassB {}
        |
        |class OtherClassC {}
        |
        |class OtherClassD {}
        |
        |class OtherClassE {}
        |
        |class OtherClassF {}
        |""".stripMargin
    )
    myFixture.addFileToProject("bar/DataHolder.scala",
      """package bar
        |
        |case class DataHolder(data: Double)
        |
        |class BarOtherClassA {}
        |
        |class BarOtherClassB {}
        |
        |class BarOtherClassC {}
        |
        |class BarOtherClassD {}
        |
        |class BarOtherClassE {}
        |
        |class BarOtherClassF {}
        |""".stripMargin
    )
    doTest(
      """import bar._
        |import foo.{DataHolder, OtherClassA, OtherClassB, OtherClassC, OtherClassD, OtherClassE, OtherClassF}
        |
        |trait DataReader {
        |  def readData(dataHolder: DataHolder): String = dataHolder match {
        |    case DataHolder(str) => str.concat("other")
        |  }
        |
        |  def useOtherClass(a: OtherClassA, b: OtherClassB, c: OtherClassC, d: OtherClassD, e: OtherClassE, f: OtherClassF)
        |  def useBarOtherClass(a: BarOtherClassA, b: BarOtherClassB, c: BarOtherClassC, d: BarOtherClassD, e: BarOtherClassE, f: BarOtherClassF)
        |}""".stripMargin,
      """import bar._
        |import foo.{DataHolder, _}
        |
        |trait DataReader {
        |  def readData(dataHolder: DataHolder): String = dataHolder match {
        |    case DataHolder(str) => str.concat("other")
        |  }
        |
        |  def useOtherClass(a: OtherClassA, b: OtherClassB, c: OtherClassC, d: OtherClassD, e: OtherClassE, f: OtherClassF)
        |  def useBarOtherClass(a: BarOtherClassA, b: BarOtherClassB, c: BarOtherClassC, d: BarOtherClassD, e: BarOtherClassE, f: BarOtherClassF)
        |}""".stripMargin,
      "Removed 6 imports, added 1 import"
    )
  }
}

class OptimizeImportsWildcardTest_2_12 extends OptimizeImportsWildcardCommon_212_213_Test {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_12

  private val CodeAfter_Common_212 =
    """package org.example.declaration.data
      |
      |import scala.util._
      |
      |//noinspection TypeAnnotation
      |object UsageSameTargetPackage1 {
      |
      |  def main(args: Array[String]) = {
      |    println(this.getClass)
      |    println(classOf[Random])
      |    println(classOf[Option[_]])
      |    println(Properties.versionString)
      |    println()
      |  }
      |}""".stripMargin

  override def testClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage(): Unit = {
    addCommonDeclarationsWithNameClashes()
    doTest(
      CodeBefore_ClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage,
      CodeAfter_Common_212,
      "Removed 2 imports"
    )
  }

  override def testClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_1(): Unit = {
    addCommonDeclarationsWithNameClashes()
    doTest(
      CodeBefore_ClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_1,
      CodeAfter_Common_212,
      "Removed 2 imports"
    )
  }

  override def testClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_2(): Unit = {
    addCommonDeclarationsWithNameClashes()
    doTest(
      CodeBefore_ClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_2,
      CodeAfter_Common_212,
      "Removed 3 imports"
    )
  }
}

class OptimizeImportsWildcardTest_2_13 extends OptimizeImportsWildcardCommon_212_213_Test {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  private val CodeAfter_Common_213 =
    """package org.example.declaration.data
      |
      |import org.example.declaration.data.Random
      |
      |import scala.util._
      |
      |//noinspection TypeAnnotation
      |object UsageSameTargetPackage1 {
      |
      |  def main(args: Array[String]) = {
      |    println(this.getClass)
      |    println(classOf[Random])
      |    println(classOf[Option[_]])
      |    println(Properties.versionString)
      |    println()
      |  }
      |}""".stripMargin

  override def testClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage(): Unit = {
    addCommonDeclarationsWithNameClashes()
    doTest(
      CodeBefore_ClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage,
      CodeAfter_Common_213,
      "Removed 1 import"
    )
  }

  override def testClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_1(): Unit = {
    addCommonDeclarationsWithNameClashes()
    doTest(
      CodeBefore_ClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_1,
      CodeAfter_Common_213,
      "Removed 1 import"
    )
  }

  override def testClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_2(): Unit = {
    addCommonDeclarationsWithNameClashes()
    doTest(
      CodeBefore_ClassName_FromWildcardImportAndDefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage_2,
      CodeAfter_Common_213,
      "Removed 2 imports"
    )
  }
}

class OptimizeImportsWildcardTest_2_13_XSource3 extends OptimizeImportsWildcardTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  override def testShadowAndSelectors(): Unit = doTest(
    """object A {
      |  class X
      |  class Y
      |
      |  implicit val s: String = ""
      |}
      |
      |object B1 {
      |  import A.{X => Z, s => _}
      |  new Z
      |}
      |
      |object B2 {
      |  import A.{Y, X => Z, s => _, _}
      |  (new Y, new Z)
      |}
      |
      |object B3 {
      |  import A.{X => _, _}
      |  new Y
      |}
      |
      |object B4 {
      |  import A.{s => implicitString, X => Z, _}
      |  (new Y, new Z)
      |}
      |
      |object B5 {
      |  import A.{s => implicitString, X => Z, _}
      |
      |  def foo(implicit s: String) = s
      |  foo
      |
      |  new Y
      |}
      |
      |object B6 {
      |  import A.{Y, X => Z, s => _, _}
      |  (new Y, new Z)
      |}
      |""".stripMargin,
    """object A {
      |  class X
      |  class Y
      |
      |  implicit val s: String = ""
      |}
      |
      |object B1 {
      |  import A.{X => Z, s => _}
      |  new Z
      |}
      |
      |object B2 {
      |  import A.{Y, X => Z, s => _}
      |  (new Y, new Z)
      |}
      |
      |object B3 {
      |  import A.{X => _, _}
      |  new Y
      |}
      |
      |object B4 {
      |  import A.{X => Z, _}
      |  (new Y, new Z)
      |}
      |
      |object B5 {
      |  import A.{s => implicitString, _}
      |
      |  def foo(implicit s: String) = s
      |  foo
      |
      |  new Y
      |}
      |
      |object B6 {
      |  import A.{Y, X => Z, s => _}
      |  (new Y, new Z)
      |}
      |""".stripMargin,
    "Removed 4 imports"
  )
}
