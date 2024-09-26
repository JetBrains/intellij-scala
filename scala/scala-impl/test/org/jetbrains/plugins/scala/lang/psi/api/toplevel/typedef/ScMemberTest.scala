package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert.{assertEquals, assertTrue, fail}

/**
 * Related tests:
 *  - [[org.jetbrains.plugins.scala.actions.ScalaQualifiedNameProviderTest]]
 *  - [[org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManagerTest]]
 */
class ScMemberTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  protected def doTestTopLevelQualifier(memberName: String, expectedTopLevelQualifier: Option[String]): Unit = {
    val file = myFixture.getFile
    assertTrue("can't find scala file in project", file.is[ScalaFile])

    val member: ScMember = file.breadthFirst()
      .collectFirst {
        case m: PsiNamedElement if m.name == memberName =>
          //nameContext will return the whole val/var definition
          m.nameContext.asInstanceOf[ScMember]
      }
      .getOrElse(fail(s"couldn't find member with name `$memberName`").asInstanceOf[Nothing])

    val topLevelQualifier = member.topLevelQualifier
    val isTopLevel = member.isTopLevel

    assertEquals(expectedTopLevelQualifier, topLevelQualifier)
    assertTrue(
      s"""`isTopLevel` should be equal to `topLevelQualifier.nonEmpty`
         |memberName : $memberName
         |isTopLevel        : $isTopLevel
         |topLevelQualifier : $topLevelQualifier
         |""".stripMargin,
      topLevelQualifier.nonEmpty == isTopLevel
    )
  }

  protected val CommonDefinitions1 =
    """class MyClass1
      |trait MyTrait1
      |object MyObject1
      |type MyTypeAlias1 = AliasedClass1
      |class AliasedClass1
      |
      |def myFunction1 = ???
      |val myValue1 = ???
      |var myVariable1 = ???
      |val (myValueFromPattern11, myValueFromPattern12) = (???, ???)
      |
      |//Scala3-specific
      |enum MyEnum1 { case MyEnum1Case1, MyEnum1Case2 }
      |given myGiven1: String = ???
      |extension (s: String)
      |  def myExtension1: String = ???
      |""".stripMargin.trim

  protected val CommonDefinitions2 =
    """class MyClass2
      |trait MyTrait2
      |object MyObject2
      |type MyTypeAlias2 = AliasedClass2
      |class AliasedClass2
      |
      |def myFunction2 = ???
      |val myValue2 = ???
      |var myVariable2 = ???
      |val (myValueFromPattern21, myValueFromPattern22) = (???, ???)
      |
      |//Scala3-specific
      |enum MyEnum2 { case MyEnum2Case1, MyEnum2Case2 }
      |given myGiven2: String = ???
      |extension (s: String)
      |  def myExtension2: String = ???
      |""".stripMargin.trim

  def testApiMethods_TopLevelQualifier_WithPackageStatement(): Unit = {
    configureScalaFromFileText(
      s"""package org
         |package example
         |
         |$CommonDefinitions1
         |
         |package extra {
         |$CommonDefinitions2
         |}
         |""".stripMargin
    )

    //NOTE: val,var are commented, cause it's not clear what to do with them right now
    // they are not ScMember instances and it's not clear whether they should be
    // see problem description in org.jetbrains.plugins.scala.extensions.PsiMemberExt#qualifiedNameOpt

    doTestTopLevelQualifier("MyClass1", Some("org.example"))
    doTestTopLevelQualifier("MyTrait1", Some("org.example"))
    doTestTopLevelQualifier("MyObject1", Some("org.example"))
    doTestTopLevelQualifier("MyTypeAlias1", Some("org.example"))
    doTestTopLevelQualifier("myFunction1", Some("org.example"))
    doTestTopLevelQualifier("myValue1", Some("org.example"))
    doTestTopLevelQualifier("myVariable1", Some("org.example"))
    doTestTopLevelQualifier("myValueFromPattern11", Some("org.example"))
    doTestTopLevelQualifier("myValueFromPattern12", Some("org.example"))
    doTestTopLevelQualifier("MyEnum1", Some("org.example"))
    doTestTopLevelQualifier("MyEnum1Case1", None)
    doTestTopLevelQualifier("myGiven1", Some("org.example"))
    doTestTopLevelQualifier("myExtension1", Some("org.example"))

    doTestTopLevelQualifier("MyClass2", Some("org.example.extra"))
    doTestTopLevelQualifier("MyTrait2", Some("org.example.extra"))
    doTestTopLevelQualifier("MyObject2", Some("org.example.extra"))
    doTestTopLevelQualifier("MyTypeAlias2", Some("org.example.extra"))
    doTestTopLevelQualifier("myFunction2", Some("org.example.extra"))
    doTestTopLevelQualifier("myValue2", Some("org.example.extra"))
    doTestTopLevelQualifier("myVariable2", Some("org.example.extra"))
    doTestTopLevelQualifier("myValueFromPattern22", Some("org.example.extra"))
    doTestTopLevelQualifier("myValueFromPattern22", Some("org.example.extra"))
    doTestTopLevelQualifier("MyEnum2", Some("org.example.extra"))
    doTestTopLevelQualifier("MyEnum2Case2", None)
    doTestTopLevelQualifier("myGiven2", Some("org.example.extra"))
    doTestTopLevelQualifier("myExtension2", Some("org.example.extra"))
  }

  def testApiMethods_TopLevelQualifier_WithoutPackageStatement(): Unit = {
    configureScalaFromFileText(
      s"""$CommonDefinitions1
         |""".stripMargin
    )

    doTestTopLevelQualifier("MyClass1", Some(""))
    doTestTopLevelQualifier("MyTrait1", Some(""))
    doTestTopLevelQualifier("MyObject1", Some(""))
    doTestTopLevelQualifier("MyTypeAlias1", Some(""))
    doTestTopLevelQualifier("myFunction1", Some(""))
    doTestTopLevelQualifier("myValue1", Some(""))
    doTestTopLevelQualifier("myVariable1", Some(""))
    doTestTopLevelQualifier("myValueFromPattern11", Some(""))
    doTestTopLevelQualifier("myValueFromPattern12", Some(""))
    doTestTopLevelQualifier("MyEnum1", Some(""))
    doTestTopLevelQualifier("MyEnum1Case1", None)
    doTestTopLevelQualifier("myGiven1", Some(""))
    doTestTopLevelQualifier("myExtension1", Some(""))
  }

  def testApiMethods_TopLevelQualifier_LocalMembers_WithPackageStatement(): Unit = {
    configureScalaFromFileText(
      s"""package org.example
         |
         |object OuterObject {
         |  $CommonDefinitions1
         |}
         |
         |class OuterClass {
         |  $CommonDefinitions2
         |}
         |""".stripMargin
    )

    doTestTopLevelQualifier("MyClass1", None)
    doTestTopLevelQualifier("MyTrait1", None)
    doTestTopLevelQualifier("MyObject1", None)
    doTestTopLevelQualifier("MyTypeAlias1", None)
    doTestTopLevelQualifier("myFunction1", None)
    doTestTopLevelQualifier("myValue1", None)
    doTestTopLevelQualifier("myVariable1", None)
    doTestTopLevelQualifier("myValueFromPattern11", None)
    doTestTopLevelQualifier("myValueFromPattern12", None)
    doTestTopLevelQualifier("MyEnum1", None)
    doTestTopLevelQualifier("MyEnum1Case1", None)
    doTestTopLevelQualifier("myGiven1", None)
    doTestTopLevelQualifier("myExtension1", None)

    doTestTopLevelQualifier("MyClass2", None)
    doTestTopLevelQualifier("MyTrait2", None)
    doTestTopLevelQualifier("MyObject2", None)
    doTestTopLevelQualifier("MyTypeAlias2", None)
    doTestTopLevelQualifier("myFunction2", None)
    doTestTopLevelQualifier("myValue2", None)
    doTestTopLevelQualifier("myVariable2", None)
    doTestTopLevelQualifier("myValueFromPattern22", None)
    doTestTopLevelQualifier("myValueFromPattern22", None)
    doTestTopLevelQualifier("MyEnum2", None)
    doTestTopLevelQualifier("myGiven2", None)
    doTestTopLevelQualifier("myExtension2", None)
  }

  def testApiMethods_TopLevelQualifier_LocalMembers_WithoutPackageStatement(): Unit = {
    configureScalaFromFileText(
      s"""object OuterObject {
         |  $CommonDefinitions1
         |}
         |
         |class OuterClass {
         |  $CommonDefinitions2
         |}
         |""".stripMargin
    )

    doTestTopLevelQualifier("MyClass1", None)
    doTestTopLevelQualifier("MyTrait1", None)
    doTestTopLevelQualifier("MyObject1", None)
    doTestTopLevelQualifier("MyTypeAlias1", None)
    doTestTopLevelQualifier("myFunction1", None)
    doTestTopLevelQualifier("myValue1", None)
    doTestTopLevelQualifier("myVariable1", None)
    doTestTopLevelQualifier("myValueFromPattern11", None)
    doTestTopLevelQualifier("myValueFromPattern12", None)
    doTestTopLevelQualifier("MyEnum1", None)
    doTestTopLevelQualifier("MyEnum1Case1", None)
    doTestTopLevelQualifier("myGiven1", None)
    doTestTopLevelQualifier("myExtension1", None)

    doTestTopLevelQualifier("MyClass2", None)
    doTestTopLevelQualifier("MyTrait2", None)
    doTestTopLevelQualifier("MyObject2", None)
    doTestTopLevelQualifier("MyTypeAlias2", None)
    doTestTopLevelQualifier("myFunction2", None)
    doTestTopLevelQualifier("myValue2", None)
    doTestTopLevelQualifier("myVariable2", None)
    doTestTopLevelQualifier("myValueFromPattern22", None)
    doTestTopLevelQualifier("myValueFromPattern22", None)
    doTestTopLevelQualifier("MyEnum2", None)
    doTestTopLevelQualifier("myGiven2", None)
    doTestTopLevelQualifier("myExtension2", None)
  }
}