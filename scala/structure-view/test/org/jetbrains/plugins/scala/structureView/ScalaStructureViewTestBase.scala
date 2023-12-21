package org.jetbrains.plugins.scala.structureView

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.ide.util.treeView.smartTree.{TreeElement, TreeElementWrapper}
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.ElementBase
import com.intellij.testFramework.{PlatformTestUtil, UsefulTestCase}
import com.intellij.ui.{IconManager, PlatformIcons}
import com.intellij.util.ui.tree.TreeUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewTestBase.Node
import org.jetbrains.plugins.scala.util.IconUtils
import org.junit.Assert

import java.util.Comparator
import javax.swing.Icon
import scala.jdk.CollectionConverters.{CollectionHasAsScala, IterableHasAsJava}
import scala.util.matching.Regex

abstract class ScalaStructureViewTestBase extends ScalaLightCodeInsightFixtureTestCase {

  protected val FinalMark = AllIcons.Nodes.FinalMark
  protected val Private = AllIcons.Nodes.Private

  protected val DeprecatedAttributesKey = ScalaStructureViewTestBase.DeprecatedAttributesKey

  protected def scalaLanguage: com.intellij.lang.Language

  override def isIconRequired: Boolean = true

  override def setUp(): Unit = {
    super.setUp()
    IconUtils.registerIconLayersInIconManager()
  }

  protected def check(@Language("Scala") code: String, nodes: Node*): Unit = {
    val structureView = createScalaStructureView(code)
    val model = structureView.getTreeModel

    val sorter: Seq[TreeElement] => Seq[TreeElement] = elements => {
      val comparators = model.getSorters.filterNot(_.isVisible).reverseIterator.map(_.getComparator.asInstanceOf[Comparator[TreeElement]])
      comparators.foldLeft(elements)((elements, comparator) => elements.sortWith((e1, e2) => comparator.compare(e1, e2) <= 0))
    }

    val actualNode: Node = Node(model.getRoot, sorter)

    val expectedNode = new Node(ScalaFileType.INSTANCE.getIcon, "foo.scala", nodes: _*)

    val expectedStr = expectedNode.presentation()
    val actualStr   = actualNode.presentation()
    Assert.assertEquals(expectedStr, actualStr)
  }

  protected def createScalaStructureView(code: String): StructureViewComponent = {
    val file = createScalaFile(code)(getProject)

    val builder = new ScalaStructureViewBuilder(file)
    val component = builder.createStructureView(null /*unused editor*/ , getProject).asInstanceOf[StructureViewComponent]
    Disposer.register(getTestRootDisposable, component) //to avoid "Thread Leaked" exceptions
    component
  }

  protected def check(structureView: StructureViewComponent, expectedNodes: Node*): Unit = {
    val model = structureView.getTreeModel

    val sorter: Seq[TreeElement] => Seq[TreeElement] = elements => {
      val comparators = model.getSorters.filterNot(_.isVisible).reverseIterator.map(_.getComparator.asInstanceOf[Comparator[TreeElement]])
      comparators.foldLeft(elements)((elements, comparator) => elements.sortWith((e1, e2) => comparator.compare(e1, e2) <= 0))
    }
    val rootNode = model.getRoot
    val actualNode: Node = Node(rootNode, sorter)

    val expectedNode = new Node(ScalaFileType.INSTANCE.getIcon, "foo.scala", expectedNodes: _*)

    val expectedStr = expectedNode.presentation()
    val actualStr   = actualNode.presentation()
    Assert.assertEquals(expectedStr, actualStr)
  }

  protected def createScalaFile(@Language("Scala") text: String)(project: Project): ScalaFile = {
    PsiFileFactory.getInstance(project)
      .createFileFromText("foo.scala", scalaLanguage, text: CharSequence)
      .asInstanceOf[ScalaFile]
  }

  protected def layered(icons: Icon*): Icon = IconUtils.createLayeredIcon(icons: _*)

  /**
   * Set a caret position ([[CARET]]) for each element you want to check.
   *
   * NOTE: Uses real editor to move caret so proper Scala version must be set inside the test class
   * (for example by overriding supportedIn)
   *
   * @param expectedNodes expected structure view tree nodes. One for each caret position
   */
  protected def checkNavigationFromSource(@Language("Scala") code: String,
                                          expectedNodes: Node*): Unit = {
    configureFromFileText(code)
    myFixture.testStructureView { structureViewComponent =>
      PlatformTestUtil.expandAll(structureViewComponent.getTree)
      val caretModel = getEditor.getCaretModel

      val offsets = caretModel.getAllCarets.asScala.map(_.getOffset)
      // reset carets to select elements one by one below
      caretModel.removeSecondaryCarets()
      caretModel.moveToOffset(0)

      val actualNodePresentations = offsets.map { offset =>
        caretModel.moveToOffset(offset)

        val currentEditorElement = structureViewComponent.getTreeModel.getCurrentEditorElement
        val structureViewTreePath = PlatformTestUtil.waitForPromise(structureViewComponent.select(currentEditorElement, false))
        val treeElementWrapper = TreeUtil.getLastUserObject(classOf[TreeElementWrapper], structureViewTreePath)
        val treeElement = treeElementWrapper.getValue.asInstanceOf[StructureViewTreeElement]

        val actualNode = Node.withoutChildren(treeElement)
        actualNode.presentation()
      }

      UsefulTestCase.assertOrderedEquals(
        actualNodePresentations.asJava,
        expectedNodes.map(_.presentation()).asJava)
    }
  }
}

private object ScalaStructureViewTestBase {
  private val DeprecatedAttributesKey = CodeInsightColors.DEPRECATED_ATTRIBUTES

  // Example of Icon.toString:
  // Row icon. myIcons=[Layered icon 16x16. myIcons=[UrlResolver{ownerClass=org.jetbrains.plugins.scala.icons.Icons, classLoader=jdk.internal.loader.ClassLoaders$AppClassLoader@61064425, overriddenPath='/org/jetbrains/plugins/scala/images/class_scala.svg', url=file:/unresolved, useCacheOnLoad=true}, nodes/finalMark.svg], nodes/c_public.svg]
  // we need just: [class_scala, finalMark, c_public]
  private final val IconFileName = new Regex("(?<=/)[^/]+(?=\\.\\w+)") // detect any extension

  class Node(icon: Icon, name: String, textAttributesKey: Option[TextAttributesKey], children: Node*) {
    def this(icon: Icon, name: String, children: Node*) = this(icon, name, None, children: _*)
    def this(icon: Icon, name: String, textAttributesKey: TextAttributesKey, children: Node*) =
      this(icon, name, Some(textAttributesKey), children: _*)

    def presentation(deep: Int = 0): String = {
      val iconString = IconFileName.findAllIn(Option(icon).mkString).mkString("[", ", ", "] ")
      val childIndent = "  " * deep
      val childrenString = children.map(_.presentation(deep + 1)).map(childIndent + _)
      iconString + (withAttributes(name) +: childrenString).mkString("\n")
    }

    private[structureView] def withChildren(children: Seq[Node]): Node =
      new Node(this.icon, this.name, this.textAttributesKey, children: _*)

    private def withAttributes(text: String): String = textAttributesKey match {
      case Some(DeprecatedAttributesKey) => s"~~~$text~~~"
      case _ => text
    }
  }

  object Node {
    def apply(baseIcon: Icon, visibilityIcon: Icon, name: String, children: Node*): Node =
      Node(baseIcon, visibilityIcon, name, None, children: _*)

    def apply(baseIcon: Icon, visibilityIcon: Icon, name: String, textAttributesKey: Option[TextAttributesKey], children: Node*): Node =
      new Node(ElementBase.buildRowIcon(baseIcon, visibilityIcon), name, textAttributesKey, children: _*)

    def apply(icon: Icon, name: String, children: Node*): Node =
      Node(icon, name, None, children: _*)

    def apply(icon: Icon, name: String, textAttributesKey: TextAttributesKey, children: Node*): Node =
      Node(icon, name, Some(textAttributesKey), children: _*)

    def apply(icon: Icon, name: String, textAttributesKey: Option[TextAttributesKey], children: Node*): Node =
      Node(icon, IconManager.getInstance.getPlatformIcon(PlatformIcons.Public), name, textAttributesKey, children: _*)

    def apply(element: StructureViewTreeElement, sorter: Seq[TreeElement] => Seq[TreeElement]): Node = {
      val node = Node.withoutChildren(element)
      val children = sorter(element.getChildren.toSeq).map { case element: StructureViewTreeElement => Node(element, sorter) }
      node.withChildren(children)
    }

    /** Useful in cases when children are irrelevant. E.g.: navigation testing */
    def withoutChildren(element: StructureViewTreeElement): Node = {
      val presentation = element.getPresentation
      val icon = presentation.getIcon(false)
      val text = presentation.getPresentableText
      val textAttributesKey = presentation.asOptionOf[ColoredItemPresentation]
        .flatMap(_.getTextAttributesKey.toOption)
      new Node(icon, text, textAttributesKey)
    }
  }
}
