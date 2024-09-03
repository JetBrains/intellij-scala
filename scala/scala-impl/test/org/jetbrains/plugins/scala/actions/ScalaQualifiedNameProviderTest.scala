package org.jetbrains.plugins.scala.actions

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.IterableOnceExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject}
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

/**
 * Also see some tests in [[org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMemberTest]]
 */
class ScalaQualifiedNameProviderTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.isScala3

  private val DeclarationsOfAllKinds: String =
    """class MyClass
      |object MyObject
      |trait MyTrait
      |enum MyEnum { case MyCase }
      |
      |val myVal1 = 1
      |val (myVal2, myVal3) = (2, 3)
      |
      |var myVar1 = 1
      |var (myVar2, myVar3) = (2, 3)
      |
      |def myFunction: String = "42"
      |
      |extension (s: String)
      |  def myExtension: String = s
      |
      |given myGivenAlias: String = "42"
      |given Short = 42
      |given myGivenDefinition: AnyRef with {}
      |
      |type MyAlias = String
      |""".stripMargin

  def testDefinitionsOfAllKinds_TopLevel(): Unit = {
    val scalaFile = configureScala3FromFileText(
      s"""package org.example
         |$DeclarationsOfAllKinds""".stripMargin
    ).asInstanceOf[ScalaFile]

    val declarations = scalaFile.members.flatMap {
      case d: ScDeclaredElementsHolder => d.declaredElements
      case m => Seq(m)
    }
    val provider = new ScalaQualifiedNameProvider
    val qualifiedNames = declarations.map(provider.getQualifiedName)
    assertCollectionEquals(
      Seq(
        "org.example.MyClass",
        "org.example.MyObject",
        "org.example.MyTrait",
        "org.example.MyEnum",
        "org.example.myVal1",
        "org.example.myVal2",
        "org.example.myVal3",
        "org.example.myVar1",
        "org.example.myVar2",
        "org.example.myVar3",
        "org.example.myFunction",
        "org.example.myExtension",
        "org.example.myGivenAlias",
        "org.example.given_Short",
        "org.example.myGivenDefinition",
        "org.example.MyAlias",
      ),
      qualifiedNames
    )
  }

  def testDefinitionsOfAllKinds_InObject(): Unit = {
    val scalaFile = configureScala3FromFileText(
      s"""package org.example
         |
         |object WrapperObject {
         |$DeclarationsOfAllKinds
         |}
         |""".stripMargin
    ).asInstanceOf[ScalaFile]

    val declarations = scalaFile.members.findByType[ScObject].toSeq.flatMap(_.members).flatMap {
      case d: ScDeclaredElementsHolder => d.declaredElements
      case m => Seq(m)
    }
    val provider = new ScalaQualifiedNameProvider
    val qualifiedNames = declarations.map(provider.getQualifiedName)
    assertCollectionEquals(
      Seq(
        "org.example.WrapperObject.MyClass",
        "org.example.WrapperObject.MyObject",
        "org.example.WrapperObject.MyTrait",
        "org.example.WrapperObject.MyEnum",
        "org.example.WrapperObject.myVal1",
        "org.example.WrapperObject.myVal2",
        "org.example.WrapperObject.myVal3",
        "org.example.WrapperObject.myVar1",
        "org.example.WrapperObject.myVar2",
        "org.example.WrapperObject.myVar3",
        "org.example.WrapperObject.myFunction",
        "org.example.WrapperObject.myExtension",
        "org.example.WrapperObject.myGivenAlias",
        "org.example.WrapperObject.given_Short",
        "org.example.WrapperObject.myGivenDefinition",
        "org.example.WrapperObject.MyAlias",
      ),
      qualifiedNames
    )
  }

  def testDefinitionsOfAllKinds_InClass(): Unit = {
    val scalaFile = configureScala3FromFileText(
      s"""package org.example
         |
         |class WrapperClass {
         |$DeclarationsOfAllKinds
         |}
         |""".stripMargin
    ).asInstanceOf[ScalaFile]

    val declarations = scalaFile.members.findByType[ScClass].toSeq.flatMap(_.members).flatMap {
      case d: ScDeclaredElementsHolder => d.declaredElements
      case m => Seq(m)
    }
    val provider = new ScalaQualifiedNameProvider
    val qualifiedNames = declarations.map(provider.getQualifiedName)

    //TODO: for classes inside other classes it should be org.example.WrapperClass#MyClass
    // instead of org.example.WrapperClass.MyClass
    // However `ScTemplateDefinition.qualifiedName` return the version with dot always.
    // It seems we should fix that method
    assertCollectionEquals(
      Seq(
        "org.example.WrapperClass.MyClass",
        "org.example.WrapperClass.MyObject",
        "org.example.WrapperClass.MyTrait",
        "org.example.WrapperClass.MyEnum",
        "org.example.WrapperClass#myVal1",
        "org.example.WrapperClass#myVal2",
        "org.example.WrapperClass#myVal3",
        "org.example.WrapperClass#myVar1",
        "org.example.WrapperClass#myVar2",
        "org.example.WrapperClass#myVar3",
        "org.example.WrapperClass#myFunction",
        "org.example.WrapperClass#myExtension",
        "org.example.WrapperClass#myGivenAlias",
        "org.example.WrapperClass#given_Short",
        "org.example.WrapperClass.myGivenDefinition",
        "org.example.WrapperClass#MyAlias",
        // The "null" corresponds to the primary constructor.
        // Its representation could be improved, but I am not sure what should be used in this case
        null
      ),
      qualifiedNames
    )
  }

  def testDefinitionsOfAllKinds_LocalScope(): Unit = {
    val scalaFile = configureScala3FromFileText(
      s"""package org.example
         |
         |def foo(): Unit = {
         |$DeclarationsOfAllKinds
         |}
         |""".stripMargin
    ).asInstanceOf[ScalaFile]

    val declarations = scalaFile.members.findByType[ScFunctionDefinition].get.body.get.asInstanceOf[ScBlockExpr].getChildren.toSeq.filterByType[ScMember].flatMap {
      case d: ScDeclaredElementsHolder => d.declaredElements
      case m => Seq(m)
    }
    val provider = new ScalaQualifiedNameProvider
    val qualifiedNames = declarations.map(provider.getQualifiedName)
    assertCollectionEquals(
      Seq(
        "MyClass",
        "MyObject",
        "MyTrait",
        "MyEnum",
        "myVal1",
        "myVal2",
        "myVal3",
        "myVar1",
        "myVar2",
        "myVar3",
        "myFunction",
        "myExtension",
        "myGivenAlias",
        "given_Short",
        "myGivenDefinition",
        "MyAlias",
      ),
      qualifiedNames
    )
  }
}