package org.jetbrains.plugins.scala.uast.platform_inspections

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.{LocalInspectionTool, NonExtendableApiUsageInspection}
import com.intellij.openapi.roots.{ModifiableRootModel, ModuleRootModificationUtil}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionTestBase, ScalaQuickFixTestFixture}
import org.jetbrains.plugins.scala.uast.platform_inspections.NonExtendableApiUsageInspectionTest.HighlightMessage
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

import java.nio.file.Path
import java.util

/**
 * Test UAST-based inspection from platform: [[com.intellij.codeInspection.NonExtendableApiUsageInspection]]
 *
 * Some parts copied from com.intellij.codeInspection.tests.kotlin.NonExtendableApiUsageInspectionTest
 */
class NonExtendableApiUsageInspectionTest extends ScalaInspectionTestBase {

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[NonExtendableApiUsageInspection]

  override protected val description: String = ""

  //Example of message: "must not be extended/implemented/overridden"
  override protected def descriptionMatches(s: String): Boolean =
    s != null && s.contains("must not be")

  override def getTestDataPath =
    super.getTestDataPath + "inspections/internal/NonExtendableApiUsageInspection/"

  override def setUpLibraries(implicit module: com.intellij.openapi.module.Module): Unit = {
    super.setUpLibraries(module)

    val libraryRootPath = getTestDataPath + "library_root"

    //without this calls, updates in `.class` files in test data will not be visible
    val libraryRootVFile = VirtualFileManager.getInstance().findFileByNioPath(Path.of(libraryRootPath))
    //before refreshing we need to iterate over children. I don't know why, but without this it's not refreshed properly
    libraryRootVFile.getChildren.foreach(_.getChildren)
    libraryRootVFile.getFileSystem.refresh(false)

    ModuleRootModificationUtil.updateModel(module, (model: ModifiableRootModel) => {
      PsiTestUtil.addProjectLibrary(model, "annotations", util.Arrays.asList(PathUtil.getJarPathForClass(classOf[org.jetbrains.annotations.ApiStatus.NonExtendable])))
      PsiTestUtil.addProjectLibrary(model, "library", util.Arrays.asList(libraryRootPath))

      PsiTestUtil.addProjectLibrary(model, "intellij_platform_utils", util.Arrays.asList(PathUtil.getJarPathForClass(classOf[com.intellij.util.messages.Topic[_]])))
    })
  }

  protected def doTest(text: String): Unit = {
    checkTextHasError(text)
  }

  def testAllInOne(): Unit = {
    val text =
      """import library.JavaClass
        |import library.JavaInterface
        |import library.JavaMethodOwner
        |import library.JavaNestedClassOwner
        |import library.JavaNonExtendableNestedOwner
        |import library.KotlinClass
        |import library.KotlinInterface
        |import library.KotlinMethodOwner
        |import library.KotlinNestedClassOwner
        |import library.KotlinNonExtendableNestedOwner
        |
        |//////////////////////////////////////
        |//Extensions of Java classes
        |//////////////////////////////////////
        |class ScalaClass_Extends_JavaClass extends JavaClass
        |class ScalaClass_Extends_JavaInterface extends JavaInterface
        |trait ScalaTrait_Extends_JavaInterface extends JavaInterface
        |object ScalaObject_Extends_JavaClass extends JavaClass
        |class ScalaClass_ExtendsNestedJavaClass extends JavaNonExtendableNestedOwner.NonExtendableNested
        |class ScalaClass_Extends_JavaClass_ByFqn extends library.JavaClass
        |
        |class Scala_Overrides_JavaMethod_NoExplicitReturnType extends JavaMethodOwner {
        |  override def doNotOverride() = ()
        |}
        |class Scala_Overrides_JavaMethod_ExplicitUnitReturnType extends JavaMethodOwner {
        |  override def doNotOverride(): Unit = ()
        |}
        |
        |//no warning
        |class ScalaClass_Extend_ExtendableJavaNestedClass extends JavaNestedClassOwner.NestedClass {}
        |
        |//////////////////////////////////////
        |//Extensions of Kotlin classes
        |//////////////////////////////////////
        |class ScalaClass_Extends_KotlinClass extends KotlinClass
        |class ScalaClass_Extends_KotlinInterface extends KotlinInterface
        |trait ScalaTrait_Extends_KotlinInterface extends KotlinInterface
        |object ScalaObject_Extends_KotlinClass extends KotlinClass
        |class ScalaClass_ExtendsNestedKotlinClass extends KotlinNonExtendableNestedOwner.NonExtendableNested
        |class ScalaClass_Extends_KotlinClass_ByFqn extends library.KotlinClass
        |
        |class Scala_Overrides_KotlinMethod_NoExplicitReturnType extends KotlinMethodOwner {
        |  override def doNotOverride() = ()
        |}
        |class Scala_Overrides_KotlinMethod_ExplicitUnitReturnType extends KotlinMethodOwner {
        |  override def doNotOverride(): Unit = ()
        |}
        |
        |//no warning
        |class ScalaClass_Extend_ExtendableKotlinNestedClass extends KotlinNestedClassOwner.NestedClass {}
        |
        |class AnonymousClasses {
        |  new JavaClass() {}
        |  new JavaInterface() {}
        |  new library.JavaClass() {}
        |  new JavaNonExtendableNestedOwner.NonExtendableNested() {}
        |
        |  new KotlinClass() {}
        |  new KotlinInterface() {}
        |  new KotlinNonExtendableNestedOwner.NonExtendableNested() {}
        |
        |  new JavaMethodOwner() { override def doNotOverride() = () }
        |  new KotlinMethodOwner() { override def doNotOverride() = () }
        |
        |  new JavaMethodOwner() { override def doNotOverride(): Unit = () }
        |  new KotlinMethodOwner() { override def doNotOverride(): Unit = () }
        |
        |  //No warnings
        |  new JavaNestedClassOwner.NestedClass() {}
        |  new KotlinNestedClassOwner.NestedClass() {}
        |}
        |""".stripMargin

    configureByText(text)
    val actualHighlights = findMatchingHighlightings(text)

    val actualHighlightMessages: Seq[HighlightMessage] = actualHighlights.map(HighlightMessage.apply)
    assertCollectionEquals(
      Seq[HighlightMessage](
        HighlightMessage((491, 500), "Class 'library.JavaClass' must not be extended"),
        HighlightMessage((548, 561), "Interface 'library.JavaInterface' must not be implemented"),
        HighlightMessage((609, 622), "Interface 'library.JavaInterface' must not be extended"),
        HighlightMessage((668, 677), "Class 'library.JavaClass' must not be extended"),
        HighlightMessage((726, 774), "Class 'library.JavaNonExtendableNestedOwner.NonExtendableNested' must not be extended"),
        HighlightMessage((824, 841), "Class 'library.JavaClass' must not be extended"),
        HighlightMessage((938, 951), "Method 'doNotOverride()' must not be overridden"),
        HighlightMessage((1058, 1071), "Method 'doNotOverride()' must not be overridden"),
        HighlightMessage((1350, 1361), "Class 'library.KotlinClass' must not be extended"),
        HighlightMessage((1411, 1426), "Interface 'library.KotlinInterface' must not be implemented"),
        HighlightMessage((1476, 1491), "Interface 'library.KotlinInterface' must not be extended"),
        HighlightMessage((1539, 1550), "Class 'library.KotlinClass' must not be extended"),
        HighlightMessage((1601, 1651), "Class 'library.KotlinNonExtendableNestedOwner.NonExtendableNested' must not be extended"),
        HighlightMessage((1703, 1722), "Class 'library.KotlinClass' must not be extended"),
        HighlightMessage((1823, 1836), "Method 'doNotOverride()' must not be overridden"),
        HighlightMessage((1947, 1960), "Method 'doNotOverride()' must not be overridden"),
        HighlightMessage((2120, 2129), "Class 'library.JavaClass' must not be extended"),
        HighlightMessage((2141, 2154), "Interface 'library.JavaInterface' must not be implemented"),
        HighlightMessage((2166, 2183), "Class 'library.JavaClass' must not be extended"),
        HighlightMessage((2195, 2243), "Class 'library.JavaNonExtendableNestedOwner.NonExtendableNested' must not be extended"),
        HighlightMessage((2256, 2267), "Class 'library.KotlinClass' must not be extended"),
        HighlightMessage((2279, 2294), "Interface 'library.KotlinInterface' must not be implemented"),
        HighlightMessage((2306, 2356), "Class 'library.KotlinNonExtendableNestedOwner.NonExtendableNested' must not be extended"),
        HighlightMessage((2402, 2415), "Method 'doNotOverride()' must not be overridden"),
        HighlightMessage((2466, 2479), "Method 'doNotOverride()' must not be overridden"),
        HighlightMessage((2529, 2542), "Method 'doNotOverride()' must not be overridden"),
        HighlightMessage((2599, 2612), "Method 'doNotOverride()' must not be overridden"),
      ),
      actualHighlightMessages
    )
  }

  def `test com.intellij.util.messages.Topic`(): Unit = {
    val text =
      """import com.intellij.util.messages.Topic
        |
        |//with explicit type element
        |object MyTopicClass extends Topic[String](classOf[String])
        |class MyTopicObject extends Topic[String](classOf[String])
        |trait MyTopicTrait extends Topic[String]
        |
        |//without explicit type element
        |object MyTopicClass extends Topic(classOf[String])
        |class MyTopicObject extends Topic(classOf[String])
        |trait MyTopicTrait extends Topic
        |""".stripMargin

    configureByText(text)
    val actualHighlights = findMatchingHighlightings(text)

    val actualHighlightMessages: Seq[HighlightMessage] = actualHighlights.map(HighlightMessage.apply)
    assertCollectionEquals(
      Seq[HighlightMessage](
        HighlightMessage((98,103),"Class 'com.intellij.util.messages.Topic' must not be extended"),
        HighlightMessage((157,162),"Class 'com.intellij.util.messages.Topic' must not be extended"),
        HighlightMessage((215,220),"Class 'com.intellij.util.messages.Topic' must not be extended"),

        HighlightMessage((290,295),"Class 'com.intellij.util.messages.Topic' must not be extended"),
        HighlightMessage((341,346),"Class 'com.intellij.util.messages.Topic' must not be extended"),
        HighlightMessage((391,396),"Class 'com.intellij.util.messages.Topic' must not be extended"),
      ),
      actualHighlightMessages
    )
  }
}

object NonExtendableApiUsageInspectionTest {
  case class HighlightMessage(range: TextRange, description: String)
  object HighlightMessage {
    def apply(range: (Int, Int), description: String): HighlightMessage =
      new HighlightMessage(TextRange.create(range._1, range._2), description)

    def apply(info: HighlightInfo): HighlightMessage = {
      val range = TextRange.create(info.getStartOffset, info.getEndOffset)
      HighlightMessage(range, info.getDescription)
    }
  }
}
