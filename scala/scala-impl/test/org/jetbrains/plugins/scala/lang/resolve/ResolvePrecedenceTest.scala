package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.lang.ASTNode
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import org.jetbrains.plugins.scala.extensions.{OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScExtensionBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.{REFSRC, REFTGT}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

import scala.collection.mutable

abstract class ResolvePrecedenceTest extends SimpleResolveTestBase {

  //keeping hard refs to AST nodes to avoid flaky tests as a workaround for SCL-20527 (see solution proposals)
  private val myASTHardRefs = new mutable.ArrayBuffer[ASTNode]

  protected def addFileToProjectKeepingHardRefsToAst(relativePath: String, fileText: String): PsiFile = {
    val file = myFixture.addFileToProject(relativePath, fileText)
    myASTHardRefs += file.getNode
    file
  }

  override def tearDown(): Unit = {
    myASTHardRefs.clear()
    super.tearDown()
  }

  //SCL-6146
  def testSCL6146(): Unit = doResolveTest(
    "case class Foo()" -> "Foo.scala",

    s"""
       |object SCL6146 {
       |  case class ${REFTGT}Foo()
       |}
       |class SCL6146 {
       |  import SCL6146._
       |  def foo(c: Any) = c match {
       |    case f: ${REFSRC}Foo =>
       |  }
       |
       |  def foo2 = Foo()
       |}
       |""".stripMargin -> "SCL6146.scala"
  )

  //SCL-16538
  def testSCL16538(): Unit = {
    doResolveTest(
      s"""
         |package outerP
         |
         |class OuterObj {
         |  def test(): Unit = ()
         |}
         |
         |object OuterObj {
         |  def ${REFTGT}instance: OuterObj = ???
         |}
         |""".stripMargin -> "OuterObj.scala"
      ,
      s"""
         |package outerP
         |package innerP
         |
         |import OuterObj.{instance => OuterObj} // <- this import does not shadow outerP.OuterObj and is therefor shown as unused
         |
         |object InnerObj {
         |  ${REFSRC}OuterObj.test() // this will fail because intellij thinks OuterObj refers to outerP.OuterObj
         |}
         |
         |""".stripMargin -> "InnerObj.scala")
  }

  def testImportShadowsOuterPackage(): Unit = {
    doResolveTest(
      """package outer
        |
        |object Clash
        |""".stripMargin -> "OuterClash.scala",

      s"""package other
         |
         |object ${REFTGT}Clash""".stripMargin -> "OtherClash.scala",

      s"""package outer
         |package inner
         |
         |import other.Clash
         |
         |object InnerObj {
         |  ${REFSRC}Clash
         |}
         |""".stripMargin -> "InnerObj.scala")
  }

  def testInheritedObject(): Unit = {
    doResolveTest(
      s"""
         |package shapeless
         |
         |trait Poly1 {
         |  type Case[A] = Nothing
         |  object ${REFTGT}Case
         |}
         |""".stripMargin -> "Poly1.scala",

      """
        |package shapeless
        |
        |object PolyDefns {
        |  abstract class Case
        |  object Case
        |}
        |""".stripMargin -> "PolyDefns.scala",

      """
        |package object shapeless {
        |  val poly = PolyDefns
        |}
        |""".stripMargin -> "package.scala",

      s"""
         |package test
         |
         |import shapeless._
         |import shapeless.poly._
         |
         |object Exractor extends Poly1 {
         |  val c = ${REFSRC}Case
         |}
         |""".stripMargin -> "Extractor.scala"
    )
  }

  def testClassNameFromSingleImportFromAvailablePackageClashesWithNameFromAnotherAvailablePackage_ScalaCollectionExample(): Unit = {
    doResolveTest(
      myFixture.getJavaFacade.findClass("scala.collection.Traversable"),
      s"""package scala
         |package collection
         |package mutable
         |
         |object Main {
         |  //Traversable is already available in `scala.collection.mutable.Traversable`
         |  // (which is in available package)
         |  //So this import can't be removed,
         |  //otherwise scala.collection.mutable.Traversable will be used
         |
         |  import scala.collection.Traversable
         |
         |  def main(args: Array[String]): Unit = {
         |    println(classOf[${REFSRC}Traversable[_]])
         |  }
         |}
         |""".stripMargin -> "Main.scala"
    )
  }

  def testClassNameFromSingleImportFromAvailablePackageClashesWithNameFromAnotherAvailablePackage(): Unit = {
    val commonDefs =
      """class MyClassTop1
        |class MyClassTop2[T]
        |""".stripMargin
    val commonDefsPackageObject =
      """class MyClassInPackageObject1
        |class MyClassInPackageObject2[T]
        |type MyAliasInPackageObject1 = String
        |type MyAliasInPackageObject2[T] = Seq[T]
        |val myValInPackageObject = 0
        |def myDefInPackageObject = 0
        |""".stripMargin

    addFileToProjectKeepingHardRefsToAst("aaa/package.scala",
      s"""$commonDefsPackageObject
         |""".stripMargin
    )
    addFileToProjectKeepingHardRefsToAst("aaa/defs.scala",
      s"""package aaa
         |$commonDefs
         |""".stripMargin
    )

    addFileToProjectKeepingHardRefsToAst("aaa/bbb/package.scala",
      s"""package aaa
         |
         |package object bbb {
         |$commonDefsPackageObject
         |}
         |""".stripMargin
    )
    addFileToProjectKeepingHardRefsToAst("aaa/bbb/defs.scala",
      s"""package aaa.bbb
         |$commonDefs
         |""".stripMargin
    )

    addFileToProjectKeepingHardRefsToAst("aaa/bbb/ccc/package.scala",
      s"""package aaa.bbb
         |
         |package object ccc {
         |$commonDefsPackageObject
         |}
         |""".stripMargin
    )
    addFileToProjectKeepingHardRefsToAst("aaa/bbb/ccc/defs.scala",
      s"""package aaa.bbb.ccc
         |$commonDefs
         |""".stripMargin
    )

    addFileToProjectKeepingHardRefsToAst("aaa/bbb/ccc/ddd/package.scala",
      s"""package aaa.bbb.ccc
         |
         |package object ddd {
         |$commonDefsPackageObject
         |}
         |""".stripMargin
    )
    addFileToProjectKeepingHardRefsToAst("aaa/bbb/ccc/ddd/defs.scala",
      s"""package aaa.bbb.ccc.ddd
         |$commonDefs
         |""".stripMargin
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyClassInPackageObject1"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.MyClassInPackageObject1
         |    val d1: ${REFSRC}MyClassInPackageObject1 = null
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyClassInPackageObject2"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.MyClassInPackageObject2
         |    val d2: ${REFSRC}MyClassInPackageObject2[_] = null
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyClassTop1"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.MyClassTop1
         |    val dt1: ${REFSRC}MyClassTop1 = null
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyClassTop2"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.MyClassTop2
         |    val dt2: ${REFSRC}MyClassTop2[_] = null
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyAliasInPackageObject1"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.MyAliasInPackageObject1
         |    val a1: ${REFSRC}MyAliasInPackageObject1 = null
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyAliasInPackageObject2"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.MyAliasInPackageObject2
         |    val a2: ${REFSRC}MyAliasInPackageObject2[_] = null
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyAliasInPackageObject2"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.myValInPackageObject
         |    ${REFSRC}myValInPackageObject
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyAliasInPackageObject2"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.myDefInPackageObject
         |    ${REFSRC}myDefInPackageObject
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )
  }

  protected val CommonDefinitionsOfAllTypes_Scala2 =
    """class MyClass
      |trait MyTrait
      |object MyObject
      |type MyTypeAlias = AliasedClass
      |
      |def myFunction = ???
      |val myValue = ???
      |var myVariable = ???
      |val (myValueFromPattern1, myValueFromPattern2) = (???, ???)
      |
      |class AliasedClass
      |""".stripMargin

  protected val PackageObjectInSamePackageFileName = "org/example/package_object.scala"
  protected val PackageObjectInSamePackageFileContent =
    s"""package org
       |
       |package object example {
       |$CommonDefinitionsOfAllTypes_Scala2
       |}
       |""".stripMargin

  protected val PackageObjectInOtherPackageFileName = "org/other/package_object.scala"
  protected val PackageObjectInOtherPackageFileContent =
    s"""package org
       |
       |package object other {
       |$CommonDefinitionsOfAllTypes_Scala2
       |}
       |""".stripMargin

  protected val PackageObjectInParentPackageFileName = "org/package_object.scala"
  protected val PackageObjectInParentPackageFileContent =
    s"""package object org {
       |$CommonDefinitionsOfAllTypes_Scala2
       |}
       |""".stripMargin

  protected val MainFileContent =
    """package org.example
      |
      |import org.other._
      |
      |object Main {
      |  def main(args: Array[String]): Unit = {
      |    classOf[MyClass]
      |    classOf[MyTrait]
      |    MyObject.getClass
      |    classOf[MyTypeAlias]
      |    myFunction
      |    myValue
      |    myVariable
      |    myValueFromPattern1
      |    myValueFromPattern2
      |  }
      |}
      |""".stripMargin

  protected val MainFileContentWithSeparatePackagings =
    """package org
      |package example
      |
      |import org.other._
      |
      |object Main {
      |  def main(args: Array[String]): Unit = {
      |    classOf[MyClass]
      |    classOf[MyTrait]
      |    MyObject.getClass
      |    classOf[MyTypeAlias]
      |    myFunction
      |    myValue
      |    myVariable
      |    myValueFromPattern1
      |    myValueFromPattern2
      |  }
      |}
      |""".stripMargin

  protected def doTestResolvesToFqn(code: String, referenceTextInCode: String, expectedMemberFqn: String): Unit = {
    val rootManager = ModuleRootManager.getInstance(getModule)

    def childrenRecursive(dir: VirtualFile): Seq[VirtualFile] = {
      val (dirs, files) = dir.getChildren.toSeq.partition(_.isDirectory)
      files ++ dirs.flatMap(childrenRecursive)
    }

    val srcFiles: Seq[VirtualFile] = for {
      srcRoot <- rootManager.getSourceRoots.toSeq if srcRoot.isDirectory
      srcFile <- childrenRecursive(srcRoot)
    } yield srcFile

    val psiManager = PsiManager.getInstance(getProject)

    val expectedMember = (for {
      vFile <- srcFiles
      psiFile = psiManager.findFile(vFile)
      foundElement <- findNamedElementInFile(psiFile, expectedMemberFqn)
    } yield foundElement).headOption.getOrElse {
      throw new AssertionError(s"can't find psi element for fqn: $expectedMemberFqn")
    }

    doResolveTest(
      expectedMember,
      code.replace(referenceTextInCode, REFSRC + referenceTextInCode) -> "Example.scala"
    )
  }


  //NOTE: this helper method is needed because we currently don't have some finder,
  //which finds member of arbitrary type in project by fqn
  //this is only implemented for template definitions (class,trait,...), but not for def/type/val/etc..
  protected def findNamedElementInFile(file: PsiFile, elementFqn: String): Option[ScNamedElement] = {
    def qualifier(fqn: String): String = {
      val lastDot = fqn.lastIndexOf('.')
      if (lastDot > 0) fqn.substring(0, lastDot)
      else ""
    }


    def qualifiedNameOpt(element: ScNamedElement): Option[String] = {
      import org.jetbrains.plugins.scala.extensions.PsiMemberExt
      //Hacks required to properly support ScBindingPattern in val/var and extension methods
      //for the details see comment to `org.jetbrains.plugins.scala.extensions.PsiMemberExt#qualifiedNameOpt`
      val memberContext: Option[ScMember] = element.getParent match {
        case eb: ScExtensionBody =>
          Some(eb.getParent.asInstanceOf[ScExtension])
        case _ =>
          Option(element.nameContext).filterByType[ScMember]
      }
      for {
        qualifier <- memberContext.flatMap(_.qualifiedNameOpt).map(qualifier)
      } yield Seq(qualifier, element.name).mkString(".")
    }

    file.breadthFirst().collectFirst {
      case m: ScNamedElement if qualifiedNameOpt(m).contains(elementFqn) => m
    }
  }

  def testNameClashBetweenDefinitionsFromTopWildcardImportAndSamePackage(): Unit
  def testNameClashBetweenDefinitionsFromTopWildcardImportAndParentPackagingStatement(): Unit
}

class ResolvePrecedenceTest_3 extends ResolvePrecedenceTest_2_13 {

  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_3

  protected val CommonDefinitionsOfAllTypes_Scala3 =
    """enum MyEnum
      |given myGiven: String = ???
      |extension (s: String)
      |  def myExtension: String = ???
      |""".stripMargin

  protected val TopLevelDefinitionsInSamePackageFileName = "org/example/package_object.scala"
  protected val TopLevelDefinitionsInSamePackageFileContent =
    s"""package org.example
       |
       |$CommonDefinitionsOfAllTypes_Scala2
       |$CommonDefinitionsOfAllTypes_Scala3
       |""".stripMargin

  protected val TopLevelDefinitionsInOtherPackageFileName = "org/other/package_object.scala"
  protected val TopLevelDefinitionsInOtherPackageFileContent =
    s"""package org.other
       |
       |$CommonDefinitionsOfAllTypes_Scala2
       |$CommonDefinitionsOfAllTypes_Scala3
       |""".stripMargin

  protected val TopLevelDefinitionsInParentPackageFileName = "org/package_object.scala"
  protected val TopLevelDefinitionsInParentPackageFileContent =
    s"""package org
       |
       |$CommonDefinitionsOfAllTypes_Scala2
       |$CommonDefinitionsOfAllTypes_Scala3
       |""".stripMargin

  def testNameClashBetweenDefinitionsFromTopWildcardImportAndTopLevelDefinitionInSamePackage(): Unit = {
    addFileToProjectKeepingHardRefsToAst(TopLevelDefinitionsInOtherPackageFileName, TopLevelDefinitionsInOtherPackageFileContent)
    addFileToProjectKeepingHardRefsToAst(TopLevelDefinitionsInSamePackageFileName, TopLevelDefinitionsInSamePackageFileContent)

    val mainFile =
      """package org.example
        |
        |import org.other._
        |
        |object Main {
        |  def main(args: Array[String]): Unit = {
        |    classOf[MyClass]
        |    classOf[MyTrait]
        |    MyObject.getClass
        |    classOf[MyTypeAlias]
        |    myFunction
        |    myValue
        |    myVariable
        |    myValueFromPattern1
        |    myValueFromPattern2
        |
        |    classOf[MyEnum]
        |    myGiven
        |    myExtension
        |  }
        |}
        |""".stripMargin

    doTestResolvesToFqn(mainFile, "MyClass", "org.other.MyClass")
    doTestResolvesToFqn(mainFile, "MyTrait", "org.other.MyTrait")
    doTestResolvesToFqn(mainFile, "MyObject", "org.other.MyObject")
    doTestResolvesToFqn(mainFile, "MyTypeAlias", "org.other.MyTypeAlias")
    doTestResolvesToFqn(mainFile, "myFunction", "org.other.myFunction")
    doTestResolvesToFqn(mainFile, "myValue", "org.other.myValue")
    doTestResolvesToFqn(mainFile, "myVariable", "org.other.myVariable")
    doTestResolvesToFqn(mainFile, "myValueFromPattern1", "org.other.myValueFromPattern1")
    doTestResolvesToFqn(mainFile, "myValueFromPattern2", "org.other.myValueFromPattern2")

    doTestResolvesToFqn(mainFile, "MyEnum", "org.other.MyEnum")
    doTestResolvesToFqn(mainFile, "myExtension", "org.other.myExtension")

    //myGiven wasn't imported using `given`, thus resolving to org.example.myGiven
    //TODO: replace expected fqn to `org.example.myGiven` when SCL-16166 is fixed
    doTestResolvesToFqn(mainFile, "myGiven", "org.other.myGiven")
  }

  def testNameClashBetweenDefinitionsFromTopWildcardImportAndTopLevelDefinitionInParentPackagingStatement(): Unit = {
    addFileToProjectKeepingHardRefsToAst(TopLevelDefinitionsInOtherPackageFileName, TopLevelDefinitionsInOtherPackageFileContent)
    addFileToProjectKeepingHardRefsToAst(TopLevelDefinitionsInSamePackageFileName, TopLevelDefinitionsInSamePackageFileContent)

    val mainFile =
      """package org
        |package example
        |
        |import org.other._
        |
        |object Main {
        |  def main(args: Array[String]): Unit = {
        |    classOf[MyClass]
        |    classOf[MyTrait]
        |    MyObject.getClass
        |    classOf[MyTypeAlias]
        |    myFunction
        |    myValue
        |    myVariable
        |    myValueFromPattern1
        |    myValueFromPattern2
        |
        |    classOf[MyEnum]
        |    myExtension
        |    myGiven
        |  }
        |}
        |""".stripMargin

    doTestResolvesToFqn(mainFile, "MyClass", "org.other.MyClass")
    doTestResolvesToFqn(mainFile, "MyTrait", "org.other.MyTrait")
    doTestResolvesToFqn(mainFile, "MyObject", "org.other.MyObject")
    doTestResolvesToFqn(mainFile, "MyTypeAlias", "org.other.MyTypeAlias")
    doTestResolvesToFqn(mainFile, "myFunction", "org.other.myFunction")
    doTestResolvesToFqn(mainFile, "myValue", "org.other.myValue")
    doTestResolvesToFqn(mainFile, "myVariable", "org.other.myVariable")
    doTestResolvesToFqn(mainFile, "myValueFromPattern1", "org.other.myValueFromPattern1")
    doTestResolvesToFqn(mainFile, "myValueFromPattern2", "org.other.myValueFromPattern2")

    doTestResolvesToFqn(mainFile, "MyEnum", "org.other.MyEnum")
    doTestResolvesToFqn(mainFile, "myExtension", "org.other.myExtension")

    //myGiven wasn't imported using `given`, thus resolving to org.example.myGiven
    //TODO: replace expected fqn to `org.example.myGiven` when SCL-16166 is fixed
    doTestResolvesToFqn(mainFile, "myGiven", "org.other.myGiven")
  }

  def testNameClashBetweenDefinitionsFromTopGivenImportAndTopLevelDefinitionInSamePackage(): Unit = {
    addFileToProjectKeepingHardRefsToAst(TopLevelDefinitionsInOtherPackageFileName, TopLevelDefinitionsInOtherPackageFileContent)

    addFileToProjectKeepingHardRefsToAst(TopLevelDefinitionsInSamePackageFileName, TopLevelDefinitionsInSamePackageFileContent)

    val MainFileContent_WithScala3 =
      """package org.example
        |
        |import org.other.given
        |
        |object Main {
        |  def main(args: Array[String]): Unit = {
        |    myGiven
        |  }
        |}
        |""".stripMargin

    doTestResolvesToFqn(MainFileContent_WithScala3, "myGiven", "org.other.myGiven")
  }

  def testNameClashBetweenDefinitionsFromTopGivenImportAndTopLevelDefinitionInParentPackagingStatement(): Unit = {
    addFileToProjectKeepingHardRefsToAst(TopLevelDefinitionsInOtherPackageFileName, TopLevelDefinitionsInOtherPackageFileContent)
    addFileToProjectKeepingHardRefsToAst(TopLevelDefinitionsInSamePackageFileName, TopLevelDefinitionsInSamePackageFileContent)

    val MainFileContentWithSeparatePackagings_WithScala3 =
      """package org
        |package example
        |
        |import org.other.given
        |
        |object Main {
        |  def main(args: Array[String]): Unit = {
        |    myGiven
        |  }
        |}
        |""".stripMargin
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings_WithScala3, "myGiven", "org.other.myGiven")
  }
}

class ResolvePrecedenceTest_2_13 extends ResolvePrecedenceTest {

  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_13

  def testSCL16057(): Unit = doResolveTest(
    s"""
       |package foo
       |
       |import cats.Monoid
       |object Test {
       |  val a: Mon${REFSRC}oid[Int] = ???
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package object cats {
       |  type Mo${REFTGT}noid[A] = A
       |}
       |""".stripMargin -> "DefinesRef.scala",
    s"""
       |package foo
       |trait Monoid[A]
       |""".stripMargin -> "PackageLocal.scala"
  )

  def testSCL16057_nonTopImport(): Unit = doResolveTest(
    s"""
       |package foo
       |
       |object Test {
       |  import cats.Monoid
       |
       |  val a: Mon${REFSRC}oid[Int] = ???
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package object cats {
       |  type ${REFTGT}Monoid[A] = A
       |}
       |""".stripMargin -> "DefinesRef.scala",
    s"""
       |package foo
       |trait Monoid[A]
       |""".stripMargin -> "PackageLocal.scala"
  )

  //ScalacIssue11593
  def testWildcardImportSameUnit(): Unit = doResolveTest(
    s"""
       |package foo {
       |  class ${REFTGT}Properties
       |
       |  import java.util._
       |
       |  object X extends App {
       |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
       |    bar(new Properties)
       |  }
       |}
       |""".stripMargin
  )

  //ScalacIssue11593
  def testWildcardImportOtherUnit(): Unit = doResolveTest(myFixture.getJavaFacade.findClass("java.util.Properties"),
    s"""
       |package foo {
       |  import java.util._
       |
       |  object X extends App {
       |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
       |    bar(new Properties)
       |  }
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package foo
       |
       |class Properties
       |
       |""".stripMargin -> "DefinesRef.scala"
  )

  //SCL-16305
  def testClassObjectNameClash(): Unit = testNoResolve(
    """
      |package hints
      |
      |case class Hint()
      |object Hint
      |""".stripMargin -> "hintsHint.scala",

    """
      |package implicits
      |
      |import hints.Hint
      |
      |object Hint {
      |  def addTo(hint: Hint) = ???
      |}
      |
      |""".stripMargin -> "implicitsHint.scala",

    s"""
       |import hints.Hint
       |
       |package object implicits {
       |  def add(hint: Hint) = ${REFSRC}Hint.addTo(hint) //ambiguous reference to object Hint
       |}
       |""".stripMargin -> "package.scala"
  )

  //resolves to imported object
  def testDefaultPackage(): Unit = {
    doResolveTest(
      s"""
         |object Resolvers {
         |  val sonatypeSnaps = ???
         |}
         |""".stripMargin -> "Resolvers.scala",

      s"""
         |package sbt
         |
         |object Resolvers {
         |  val ${REFTGT}sonatypeSnaps = ???
         |}""".stripMargin -> "sbtResolvers.scala",

      s"""import sbt._
         |
         |class Test {
         |  import Resolvers._
         |
         |  ${REFSRC}sonatypeSnaps
         |}
         |""".stripMargin -> "Test.scala"
    )
  }

  //SCL-16562
  def testClassName_FromWildcardImport_ClashesWith_NotExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      myFixture.getJavaFacade.findClass("scala.util.Random"),
      s"""package org.example.data
         |
         |class Random //also exists in scala.util
         |""".stripMargin -> "Random.scala",
      s"""package org.example.data
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Random = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_FromWildcardImport_ClashesWith_ExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Random //also exists in scala.util
         |""".stripMargin -> "Random.scala",
      s"""package org.example.data
         |
         |import org.example.data.Random
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Random = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_FromWildcardImport_ClashesWith_ExplicitlyImportedClass_FromOtherPackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Random //also exists in scala.util
         |""".stripMargin -> "Random.scala",
      s"""package org.example.usage
         |
         |import org.example.data.Random
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Random = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_DefaultPackage_ClashesWith_NotExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      myFixture.getJavaFacade.findClass("scala.lang.Responder"),
      s"""package org.example.data
         |
         |class ${REFTGT}Responder //also exists in scala.lang (default package)
         |""".stripMargin -> "Responder.scala",
      s"""package org.example.data
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Responder = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_DefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Responder //also exists in scala.lang (default package)
         |""".stripMargin -> "Responder.scala",
      s"""package org.example.data
         |
         |import org.example.data.Responder
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Responder = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_DefaultPackage_ClashesWith_ExplicitlyImportedClass_FromOtherPackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Responder //also exists in scala.lang (default package)
         |""".stripMargin -> "Responder.scala",
      s"""package org.example.usage
         |
         |import org.example.data.Responder
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Responder = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  override def testNameClashBetweenDefinitionsFromTopWildcardImportAndSamePackage(): Unit = {
    addFileToProjectKeepingHardRefsToAst(PackageObjectInOtherPackageFileName, PackageObjectInOtherPackageFileContent)
    addFileToProjectKeepingHardRefsToAst(PackageObjectInSamePackageFileName, PackageObjectInSamePackageFileContent)

    doTestResolvesToFqn(MainFileContent, "MyClass", "org.other.MyClass")
    doTestResolvesToFqn(MainFileContent, "MyTrait", "org.other.MyTrait")
    doTestResolvesToFqn(MainFileContent, "MyObject", "org.other.MyObject")
    doTestResolvesToFqn(MainFileContent, "MyTypeAlias", "org.other.MyTypeAlias")
    doTestResolvesToFqn(MainFileContent, "myFunction", "org.other.myFunction")
    doTestResolvesToFqn(MainFileContent, "myValue", "org.other.myValue")
    doTestResolvesToFqn(MainFileContent, "myVariable", "org.other.myVariable")
    doTestResolvesToFqn(MainFileContent, "myValueFromPattern1", "org.other.myValueFromPattern1")
    doTestResolvesToFqn(MainFileContent, "myValueFromPattern2", "org.other.myValueFromPattern2")
  }

  override def testNameClashBetweenDefinitionsFromTopWildcardImportAndParentPackagingStatement(): Unit = {
    addFileToProjectKeepingHardRefsToAst(PackageObjectInOtherPackageFileName, PackageObjectInOtherPackageFileContent)
    addFileToProjectKeepingHardRefsToAst(PackageObjectInSamePackageFileName, PackageObjectInSamePackageFileContent)

    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "MyClass", "org.other.MyClass")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "MyTrait", "org.other.MyTrait")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "MyObject", "org.other.MyObject")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "MyTypeAlias", "org.other.MyTypeAlias")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "myFunction", "org.other.myFunction")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "myValue", "org.other.myValue")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "myVariable", "org.other.myVariable")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "myValueFromPattern1", "org.other.myValueFromPattern1")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "myValueFromPattern2", "org.other.myValueFromPattern2")
  }
}

class ResolvePrecedenceTest_2_12 extends ResolvePrecedenceTest {

  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_12

  //SCL-16057
  def testSCL16057(): Unit = doResolveTest(
    s"""
       |package foo
       |
       |import cats.Monoid
       |object Test {
       |  val a: Mon${REFSRC}oid[Int] = ???
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package object cats {
       |  type Monoid[A] = A
       |}
       |""".stripMargin -> "DefinesRef.scala",
    s"""
       |package foo
       |trait M${REFTGT}onoid[A]
       |""".stripMargin -> "PackageLocal.scala"
  )

  def testSCL16057_nonTopImport(): Unit = doResolveTest(
    s"""
       |package foo
       |
       |object Test {
       |  import cats.Monoid
       |
       |  val a: Mon${REFSRC}oid[Int] = ???
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package object cats {
       |  type ${REFTGT}Monoid[A] = A
       |}
       |""".stripMargin -> "DefinesRef.scala",
    s"""
       |package foo
       |trait Monoid[A]
       |""".stripMargin -> "PackageLocal.scala"
  )

  //ScalacIssue11593
  def testWildcardImportSameUnit_topLevelImport(): Unit = doResolveTest(
    s"""
       |package foo {
       |  class ${REFTGT}Properties
       |
       |  import java.util._
       |
       |  object X extends App {
       |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
       |    bar(new Properties)
       |  }
       |}
       |""".stripMargin
  )

  def testWildcardImportSameUnit_nonTopImport(): Unit = testNoResolve(
    s"""
       |package foo {
       |  class Properties
       |
       |  object X extends App {
       |    import java.util._
       |
       |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
       |    bar(new Properties)
       |  }
       |}
       |""".stripMargin -> "HasAmbiguity.scala"
  )

  def testWildcardImportOtherUnit_topLevelImport(): Unit =
    doResolveTest(
      s"""
         |package foo {
         |  import java.util._
         |
         |  object X extends App {
         |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
         |    bar(new Properties)
         |  }
         |}
         |""".stripMargin -> "HasRef.scala",
      s"""
         |package foo
         |
         |class ${REFTGT}Properties
         |
         |""".stripMargin -> "DefinesRef.scala"
    )

  def testWildcardImportOtherUnit_nonTopImport(): Unit =
    doResolveTest(myFixture.getJavaFacade.findClass("java.util.Properties"),
      s"""
         |package foo {
         |  object X extends App {
         |    import java.util._
         |
         |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
         |    bar(new Properties)
         |  }
         |}
         |""".stripMargin -> "HasRef.scala",
      s"""
         |package foo
         |
         |class Properties
         |
         |""".stripMargin -> "DefinesRef.scala"
    )

  //SCL-16305
  def testClassObjectNameClash(): Unit = doResolveTest(
    """
      |package hints
      |
      |case class Hint()
      |object Hint
      |""".stripMargin -> "hintsHint.scala",

    s"""
       |package implicits
       |
       |import hints.Hint
       |
       |object ${REFTGT}Hint {
       |  def addTo(hint: Hint) = ???
       |}
       |
       |""".stripMargin -> "implicitsHint.scala",

    s"""
       |import hints.Hint
       |
       |package object implicits {
       |
       |  def add(hint: Hint) = ${REFSRC}Hint.addTo(hint) //object from `implicits` package shadows top-level import
       |}
       |""".stripMargin -> "package.scala"
  )

  //resolves to object from same (default) package
  def testDefaultPackage(): Unit = {
    doResolveTest(
      s"""
         |object Resolvers {
         |  val ${REFTGT}sonatypeSnaps = ???
         |}
         |""".stripMargin -> "Resolvers.scala",

      """
        |package sbt
        |
        |object Resolvers {
        |  val sonatypeSnaps = ???
        |}""".stripMargin -> "sbtResolvers.scala",

      s"""import sbt._
         |
         |class Test {
         |  import Resolvers._
         |
         |  ${REFSRC}sonatypeSnaps
         |}
         |""".stripMargin -> "Test.scala"
    )
  }

  //SCL-16562
  def testClassName_FromWildcardImport_ClashesWith_NotExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Random //also exists in scala.util
         |""".stripMargin -> "Random.scala",
      s"""package org.example.data
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Random = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_FromWildcardImport_ClashesWith_ExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Random //also exists in scala.util
         |""".stripMargin -> "Random.scala",
      s"""package org.example.data
         |
         |import org.example.data.Random
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Random = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_FromWildcardImport_ClashesWith_ExplicitlyImportedClass_FromOtherPackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Random //also exists in scala.util
         |""".stripMargin -> "Random.scala",
      s"""package org.example.usage
         |
         |import org.example.data.Random
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Random = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_DefaultPackage_ClashesWith_NotExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Responder //also exists in scala.lang (default package)
         |""".stripMargin -> "Responder.scala",
      s"""package org.example.data
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Responder = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_DefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Responder //also exists in scala.lang (default package)
         |""".stripMargin -> "Responder.scala",
      s"""package org.example.data
         |
         |import org.example.data.Responder
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Responder = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_DefaultPackage_ClashesWith_ExplicitlyImportedClass_FromOtherPackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Responder //also exists in scala.lang (default package)
         |""".stripMargin -> "Responder.scala",
      s"""package org.example.usage
         |
         |import org.example.data.Responder
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Responder = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }


  override def testNameClashBetweenDefinitionsFromTopWildcardImportAndSamePackage(): Unit = {
    addFileToProjectKeepingHardRefsToAst(PackageObjectInOtherPackageFileName, PackageObjectInOtherPackageFileContent).getNode
    addFileToProjectKeepingHardRefsToAst(PackageObjectInSamePackageFileName, PackageObjectInSamePackageFileContent).getNode

    doTestResolvesToFqn(MainFileContent, "MyClass", "org.example.MyClass")
    doTestResolvesToFqn(MainFileContent, "MyTrait", "org.example.MyTrait")
    doTestResolvesToFqn(MainFileContent, "MyObject", "org.example.MyObject")
    doTestResolvesToFqn(MainFileContent, "MyTypeAlias", "org.example.MyTypeAlias")
    doTestResolvesToFqn(MainFileContent, "myFunction", "org.example.myFunction")
    doTestResolvesToFqn(MainFileContent, "myValue", "org.example.myValue")
    doTestResolvesToFqn(MainFileContent, "myVariable", "org.example.myVariable")
    doTestResolvesToFqn(MainFileContent, "myValueFromPattern1", "org.example.myValueFromPattern1")
    doTestResolvesToFqn(MainFileContent, "myValueFromPattern2", "org.example.myValueFromPattern2")
  }

  override def testNameClashBetweenDefinitionsFromTopWildcardImportAndParentPackagingStatement(): Unit = {
    addFileToProjectKeepingHardRefsToAst(PackageObjectInOtherPackageFileName, PackageObjectInOtherPackageFileContent)
    addFileToProjectKeepingHardRefsToAst(PackageObjectInParentPackageFileName, PackageObjectInParentPackageFileName)

    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "MyClass", "org.other.MyClass")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "MyTrait", "org.other.MyTrait")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "MyObject", "org.other.MyObject")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "MyTypeAlias", "org.other.MyTypeAlias")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "myFunction", "org.other.myFunction")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "myValue", "org.other.myValue")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "myVariable", "org.other.myVariable")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "myValueFromPattern1", "org.other.myValueFromPattern1")
    doTestResolvesToFqn(MainFileContentWithSeparatePackagings, "myValueFromPattern2", "org.other.myValueFromPattern2")
  }
}