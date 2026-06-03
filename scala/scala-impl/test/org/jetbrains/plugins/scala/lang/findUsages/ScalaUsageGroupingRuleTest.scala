package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.psi.impl.ElementBase
import com.intellij.ui.{DeferredIcon, IconManager, PlatformIcons}
import com.intellij.usages.UsageGroup
import com.intellij.usages.impl.FileStructureGroupRuleProvider
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.util.IconUtils
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

import javax.swing.Icon
import scala.jdk.CollectionConverters.CollectionHasAsScala

//noinspection ApiStatus,UnstableApiUsage
class ScalaUsageGroupingRuleTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def isIconRequired: Boolean = true

  override protected def setUp(): Unit = {
    super.setUp()
    IconUtils.registerIconLayersInIconManager()
  }

  protected lazy val MethodIcon = IconManager.getInstance.getPlatformIcon(PlatformIcons.Method)
  protected lazy val PublicIcon = IconManager.getInstance.getPlatformIcon(PlatformIcons.Public)
  protected lazy val ProtectedIcon = IconManager.getInstance.getPlatformIcon(PlatformIcons.Protected)
  protected lazy val PrivateIcon = IconManager.getInstance.getPlatformIcon(PlatformIcons.Private)

  protected def layered(icons: Icon*): Icon = IconUtils.createLayeredIcon(icons: _*)

  private def unwrapIcon(icon: Icon): Icon = icon match {
    case deferred: DeferredIcon => deferred.getBaseIcon
    case _ => icon
  }

  def testUsageGroupPresentableTextAndIcons_AllInOne(): Unit = {
    myFixture.addFileToProject(
      "usages.scala",
      """
        |import definitions.MyToken
        |
        |class MyTokenUsage extends MyToken("in extends list") {
        |  new MyToken("in constructor body")
        |
        |  def myDef = new MyToken("in class -> def")
        |  protected def myDef1 = new MyToken("in class -> def")
        |  private def myDef2 = new MyToken("in class -> def")
        |
        |  val myVal = new MyToken("in class -> val")
        |  val (myVal2, myVal3) = (MyToken("in class -> val 2"), new MyToken("in class -> val 3"))
        |  type MyAlias = MyToken
        |
        |  given myGiven1: MyToken = ???
        |
        |  given myGiven2: AnyRef = new MyToken("in class -> given 2")
        |
        |  extension (s: String)
        |    def ext1: String = new MyToken("in class -> function -> extension").toString
        |
        |  def foo(): Unit = {
        |    new MyToken("in class -> function")
        |
        |    def innerFoo1(): Unit = {
        |      def innerFoo2(): Unit = {
        |        new MyToken("in class -> function -> inner function")
        |      }
        |    }
        |
        |    class InnerClassInFoo1 {
        |      class InnerClassInFoo2 {
        |        def fooInInnerClass(): Unit = {
        |          new MyToken("in class -> inner class -> function")
        |        }
        |      }
        |    }
        |  }
        |
        |  def bar(): Unit = {
        |
        |    {
        |      new Object() {
        |        new MyToken("in class -> function -> local scope -> anonymous class")
        |      }
        |    }
        |  }
        |
        |  class InnerClass {
        |    object InnerObject {
        |      new MyToken("in class -> inner class")
        |
        |      def fooInInnerClass(): Unit = {
        |        new MyToken("in class -> inner class -> function")
        |      }
        |    }
        |  }
        |}
        |"""
    )

    myFixture.configureByText(
      "MyToken.scala",
      s"""package definitions
         |class ${CARET}MyToken(name: String)""".stripMargin
    )

    val usages = myFixture.testFindUsagesUsingAction().asScala.toSeq.sortBy(_.getNavigationOffset)

    //NOTE: we use all FileStructureGroupRuleProvider implementations to be as close to the platform as possible
    //Unfortunately there is no method in IntelliJ platform which would encapsulate this
    //FileStructureGroupRuleProvider is used in `com.intellij.usages.impl.rules.ActiveRules`
    //and added to the list of all groups, which contains not just file structure groups
    val fileStructureGroupProviders = FileStructureGroupRuleProvider.EP_NAME.getExtensions

    val usagesGroups: Seq[Seq[UsageGroup]] = usages.map { usage =>
      val rules = fileStructureGroupProviders.map(_.getUsageGroupingRule(getProject)).filter(_ != null).toSeq
      rules.flatMap(_.getParentGroupsFor(usage, Array()).asScala.toSeq)
    }

    val expectedPresentableTexts = Seq(
      Seq("usages.scala"),
      Seq("usages.scala", "MyTokenUsage"),
      Seq("usages.scala", "MyTokenUsage"),
      Seq("usages.scala", "MyTokenUsage", "myDef"),
      Seq("usages.scala", "MyTokenUsage", "myDef1"),
      Seq("usages.scala", "MyTokenUsage", "myDef2"),
      Seq("usages.scala", "MyTokenUsage", "myVal"),
      Seq("usages.scala", "MyTokenUsage"),
      Seq("usages.scala", "MyTokenUsage"),
      Seq("usages.scala", "MyTokenUsage", "MyAlias"),
      Seq("usages.scala", "MyTokenUsage", "myGiven1"),
      Seq("usages.scala", "MyTokenUsage", "myGiven2"),
      Seq("usages.scala", "MyTokenUsage", "ext1"),
      Seq("usages.scala", "MyTokenUsage", "foo"),
      Seq("usages.scala", "MyTokenUsage", "foo"),
      Seq("usages.scala", "InnerClassInFoo1.InnerClassInFoo2", "fooInInnerClass"),
      Seq("usages.scala", "MyTokenUsage", "bar"),
      Seq("usages.scala", "MyTokenUsage.InnerClass.InnerObject"),
      Seq("usages.scala", "MyTokenUsage.InnerClass.InnerObject", "fooInInnerClass"),
    )
    val actualUsagePresentableTexts: Seq[Seq[String]] = usagesGroups.map(_.map(_.getPresentableGroupText))
    assertCollectionEquals(
      expectedPresentableTexts,
      actualUsagePresentableTexts
    )

    import ElementBase.{buildRowIcon => row}
    val classIcon = row(Icons.CLASS, PublicIcon)
    val objectIcon = row(Icons.OBJECT, PublicIcon)
    val fileIcon = Icons.SCALA_FILE
    val expectedUsageIcons = Seq(
      Seq(fileIcon),
      Seq(fileIcon, classIcon),
      Seq(fileIcon, classIcon),
      Seq(fileIcon, classIcon, row(MethodIcon, PublicIcon)),
      Seq(fileIcon, classIcon, row(MethodIcon, ProtectedIcon)),
      Seq(fileIcon, classIcon, row(MethodIcon, PrivateIcon)),
      Seq(fileIcon, classIcon, row(Icons.FIELD_VAL, PublicIcon)),
      Seq(fileIcon, classIcon),
      Seq(fileIcon, classIcon),
      Seq(fileIcon, classIcon, row(Icons.TYPE_ALIAS, PublicIcon)),
      Seq(fileIcon, classIcon, row(MethodIcon, PublicIcon)),
      Seq(fileIcon, classIcon, row(MethodIcon, PublicIcon)),
      Seq(fileIcon, classIcon, row(MethodIcon, PublicIcon)),
      Seq(fileIcon, classIcon, row(MethodIcon, PublicIcon)),
      Seq(fileIcon, classIcon, row(MethodIcon, PublicIcon)),
      Seq(fileIcon, classIcon, row(MethodIcon, PublicIcon)),
      Seq(fileIcon, classIcon, row(MethodIcon, PublicIcon)),
      Seq(fileIcon, objectIcon),
      Seq(fileIcon, objectIcon, row(MethodIcon, PublicIcon)),
    )
    val actualUsageIcons: Seq[Seq[Icon]] = usagesGroups.map(_.map(_.getIcon).map(unwrapIcon))
    assertCollectionEquals(
      expectedUsageIcons,
      actualUsageIcons
    )
  }
}
