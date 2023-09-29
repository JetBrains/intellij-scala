package org.jetbrains.plugins.scala.projectView

import com.intellij.ide.projectView.impl.nodes.{ProjectViewProjectNode, PsiDirectoryNode}
import com.intellij.openapi.roots.{ModuleRootManager, ModuleRootModificationUtil}
import com.intellij.projectView.BaseProjectViewTestCase
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, inWriteAction}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.fail

import scala.jdk.CollectionConverters.CollectionHasAsScala

//TODO: review projectView subsystem and cover it with more tests
class ScalaProjectViewTest extends BaseProjectViewTestCase with ScalaSdkOwner {

  ////////////////////
  override def getTestDataPath: String = TestUtils.getTestDataPath

  override def getTestPath: String = "/projectView/scala3"
  ////////////////////

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def librariesLoaders: Seq[LibraryLoader] = Seq(ScalaSDKLoader())

  override def setUp(): Unit = {
    super.setUp()

    setUpLibraries(getModule)

    //add source root, it's required in order project view tests work properly (in order Scala SDK is detected correctly)
    inWriteAction {
      ModuleRootModificationUtil.modifyModel(getModule, model => {
        val contentEntry = model.getContentEntries.head
        contentEntry.addSourceFolder(contentEntry.getFile, false)
        true
      })
    }
  }

  def testFileIcons(): Unit = {
    val structure = super.getProjectTreeStructure

    val rootNode = structure.getRootElement.asInstanceOf[ProjectViewProjectNode]
    val rootChildren = rootNode.getChildren.asScala.toSeq

    //there should be two children in the root - sources and "external libraries"
    val contentRootNode = rootChildren.iterator.findByType[PsiDirectoryNode] match {
      case Some(dir) if ModuleRootManager.getInstance(getModule).getContentRoots.contains(dir.getValue.getVirtualFile) =>
        dir
      case _ =>
        fail(
          s"""Can't find content root node. Available nodes:
             |${rootChildren.map(_.toTestString(null)).mkString("\n")}""".stripMargin
        ).asInstanceOf[Nothing]
    }

    assertStructureEqual(
      contentRootNode.getValue,
      """PsiDirectory: fileIcons
        | PsiDirectory: mix
        |  ScalaFile: ClassAndAnotherClass.scala
        |  ScalaFile: ClassAndAnotherFun.scala
        |  ScalaFile: ClassAndAnotherTrait.scala
        |  ScalaFile: Companions_WithOtherTopLevelDefinitions.scala
        |  ScalaFile: Companions_WithOtherTypeDefinitions.scala
        |  ScalaFile: SingleClassInFile_DifferentFileName.scala
        |  ScalaFile: singleTopLevelFunction.scala
        |  ScalaFile: singleTopLevelType.scala
        |  ScalaFile: singleTopLevelValue.scala
        | PsiDirectory: well_defined_entities
        |  ScalaCompanionsFileNode: class Companions_ClassWithObject
        |  ScalaCompanionsFileNode: enum Companions_EnumWithObject
        |  ScalaCompanionsFileNode: trait Companions_TraitWithObject
        |  SingleCaseClassInFile
        |  SingleCaseObjectInFile
        |  SingleClassInFile
        |  SingleEnumInFile
        |  SingleObjectInFile
        |  SingleTraitInFile
        |""".stripMargin,
      100
    )
  }
}