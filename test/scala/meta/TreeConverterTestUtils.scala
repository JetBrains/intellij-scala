package scala.meta

import java.util.regex.Pattern

import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScCommentOwner

import scala.meta.semantic.IDEAContext

object TreeConverterTestUtils {

  private val START_TOKEN = "//start"

  def convert(text: String)
             (implicit fixture: CodeInsightTestFixture,
              semanticContext: IDEAContext): Tree = {
    val psi = psiFromText(text)
    semanticContext.ideaToMeta(psi)
  }

  def psiFromText(text: String)
                 (implicit fixture: CodeInsightTestFixture): ScalaPsiElement = {
    def nextScalaPsiElement(current: PsiElement): ScalaPsiElement = current match {
      case _: PsiWhiteSpace => nextScalaPsiElement(current.getNextSibling)
      case sce: ScalaPsiElement => sce
      case _: PsiElement => nextScalaPsiElement(current.getNextSibling)
    }

    val file: ScalaFile = parseTextToFile(text)
    val startPos = file.getText.indexOf(START_TOKEN)
    if (startPos < 0)
      file.typeDefinitions.headOption.getOrElse(file.getImportStatements.head)
    else {
      val element = file.findElementAt(startPos)
      element.getParent match {
        case parent: ScCommentOwner => parent.asInstanceOf[ScalaPsiElement]
        case _ => nextScalaPsiElement(element)
      }
    }
  }

  def structuralEquals(tree1: Tree, tree2: Tree): Boolean = {
    // NOTE: for an exhaustive list of tree field types see
    // see /foundation/src/main/scala/org/scalameta/ast/internal.scala
    def loop(x1: Any, x2: Any): Boolean = (x1, x2) match {
      case (x1: Tree, x2: Tree) => structuralEquals(x1, x2)
      case (Some(x1), Some(x2)) => loop(x1, x2)
      case (Seq(xs1@_*), Seq(xs2@_*)) => xs1.zip(xs2).forall { case (x1, x2) => loop(x1, x2) }
      case (x1, x2) => x1 == x2
    }

    def tagsEqual = true

    def fieldsEqual = tree1.productIterator.toList.zip(tree2.productIterator.toList).forall { case (x1, x2) => loop(x1, x2) }

    (tagsEqual && fieldsEqual) || {
      println(s"${tree1.show[scala.meta.Structure]} <=> ${tree2.show[scala.meta.Structure]}");
      false
    }
    true
  }

  private def parseTextToFile(@Language("Scala") string: String)
                             (implicit fixture: CodeInsightTestFixture): ScalaFile = {
    def isTopLevel = Pattern.compile("^\\s*(class|trait|object|import|package).*", Pattern.DOTALL).matcher(string).matches()

    val text = if (!isTopLevel)
      s"""
         |object Dummy {
         |${if (!string.contains(START_TOKEN)) START_TOKEN else ""}
         |$string
         |}
      """.stripMargin
    else string
    fixture.configureByText(ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
    //    fixture.checkHighlighting()
  }
}
