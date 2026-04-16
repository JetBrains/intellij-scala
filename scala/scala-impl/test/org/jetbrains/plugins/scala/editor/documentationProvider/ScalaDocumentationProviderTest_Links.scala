package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import junit.framework.TestCase.assertEquals
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.editor.documentationProvider.util.ScalaDocumentationsBodySectionTesting
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocReferenceLink
import org.junit.Assert.assertTrue

class ScalaDocumentationProviderTest_Links
  extends ScalaDocumentationProviderTestBase
    with ScalaDocumentationsBodySectionTesting {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  private val linkRegex = raw"""<a href="([^"]*)"[^>]*><code>(.*?)</code></a>""".r

  private def runTest(fileContent: String, expected: String): Unit = {
    def run(fileContent: String): Unit = {
      val (editor, file) = createEditorAndFile(fileContent)
      assertTrue("file should contain valid psi tree", file.isValid)

      val (referredElement, _) = extractReferredAndOriginalElements(editor, file)
      val actualDoc = generateRenderedDoc(referredElement)


      // First, check if the expected link is present in the generated documentation
      val matches = linkRegex.findAllMatchIn(actualDoc).toSeq

      def makeLineText(url: String, text: String): String = s"$url -> $text"

      val actual = matches
        .map(m => makeLineText(m.group(1), m.group(2)))
        .mkString("\n")

      assertEquals(expected.trim, actual.trim)

      // Next, check if the url navigates to the same entity as the link
      val links = getFixture.getFile
        .depthFirst()
        .collect { case link: ScDocReferenceLink => link }
        .toSeq

      assertEquals(matches.size, links.size)
      val manager = referredElement.getManager
      for ((m, link) <- matches.zip(links)) {
        val resolved = link.query.multiResolveScala(false)
        assert(resolved.nonEmpty, s"No results for link: ${link.getText}")

        val linkText = m.group(1)
        val target = documentationProvider.getDocumentationElementForLink(
          manager,
          linkText.stripPrefix(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL),
          referredElement
        )
        assert(
          target != null,
          s"No documentation element found for link: $linkText",
        )
        assert(
          resolved.exists(_.element == target),
          s"None of the resolved elements matches the link-target ($linkText -> $target): ${resolved.map(_.element).mkString(", ")}",
        )
      }
    }

    assert(fileContent.contains("@syntax"), "Test file should contain @syntax")
    run(fileContent.replace("@syntax", "@syntax wiki"))
    run(fileContent.replace("@syntax", "@syntax markdown"))
  }

  def test_normal(): Unit =
    runTest(
      s"""package org.example
         |/**
         | * [[Test]]
         | * [[example.Test]]
         | * [[org.example.Test]]
         | * [[_root_.org.example.Test]]
         | *
         | * [[Test Test]]
         | * [[example.Test A very simple Test]]
         | * [[org.example.Test Test]]]
         | * [[_root_.org.example.Test Test]]
         | *
         | * @syntax
         | */
         |class Te${CARET}st
         |""".stripMargin,
      """
        |psi_element://Test -> Test
        |psi_element://example.Test -> example.Test
        |psi_element://org.example.Test -> org.example.Test
        |psi_element://_root_.org.example.Test -> _root_.org.example.Test
        |psi_element://Test -> Test
        |psi_element://example.Test -> A very simple Test
        |psi_element://org.example.Test -> Test]
        |psi_element://_root_.org.example.Test -> Test
        |""".stripMargin,
    )

  def test_triple_brackets(): Unit =
    runTest(
      s"""package org.example
         |/**
         | * [[[Test]]]
         | * [[[example.Test]]]
         | * [[[org.example.Test]]]
         | * [[[_root_.org.example.Test]]]
         | *
         | * [[[Test Test]]]
         | * [[[example.Test A very simple Test]]]
         | * [[[org.example.Test Test]]]]
         | * [[[_root_.org.example.Test Test]]]
         | *
         | * @syntax
         | */
         |class Te${CARET}st
         |""".stripMargin,
      """
        |psi_element://Test -> Test
        |psi_element://example.Test -> example.Test
        |psi_element://org.example.Test -> org.example.Test
        |psi_element://_root_.org.example.Test -> _root_.org.example.Test
        |psi_element://Test -> Test
        |psi_element://example.Test -> A very simple Test
        |psi_element://org.example.Test -> Test]
        |psi_element://_root_.org.example.Test -> Test
        |""".stripMargin,
    )

  def test_link_to_sdt(): Unit =
    runTest(
      s"""package org.example
         |/**
         | * [[Seq]]
         | * [[Seq.isEmpty]]
         | * [[Seq.fill]]
         | * [[Seq.empty]]
         | * [[Seq.empty[A]]]
         | *
         | * @syntax
         | */
         |class Te${CARET}st
         |""".stripMargin,
      """
        |psi_element://Seq -> Seq
        |psi_element://Seq.isEmpty -> Seq.isEmpty
        |psi_element://Seq.fill -> Seq.fill
        |psi_element://Seq.empty -> Seq.empty
        |psi_element://Seq.empty[A] -> Seq.empty[A]
        |""".stripMargin,
    )

  // SCL-25227
  def test_type_ref(): Unit =
    runTest(
      s"""
         |class Target1 {
         |  def foo = 42
         |}
         |
         |/**
         | * Link [[Target1#foo]]
         | * Link [[Target1!#foo]]
         | *
         | * @syntax
         | */
         |object Te${CARET}st1
         |""".stripMargin,
      """
        |psi_element://Target1#foo -> Target1#foo
        |psi_element://Target1!#foo -> Target1!#foo
        |""".stripMargin
    )

  def test_ref_to_type_alias(): Unit =
    runTest(
      s"""
         |class TypeLink {
         |  type T = Int
         |}
         |
         |/**
         | * [[TypeLink.T]]
         | *
         | * @syntax
         | */
         |class Te${CARET}st
         |""".stripMargin,
      """
        |psi_element://TypeLink.T -> TypeLink.T
        |""".stripMargin
    )

  def test_ref_to_java(): Unit =
    runTest(
      s"""
         |/**
         | * [[java.lang.Thread]]
         | * [[java.lang.Thread.dumpStack()]]
         | * [[java.lang.Thread.run]]
         | * [[java.lang.Thread!.getClass]]
         | *
         | * @syntax
         | */
         |class Te${CARET}st
         |""".stripMargin,
      """
        |psi_element://java.lang.Thread -> java.lang.Thread
        |psi_element://java.lang.Thread.dumpStack() -> java.lang.Thread.dumpStack()
        |psi_element://java.lang.Thread.run -> java.lang.Thread.run
        |psi_element://java.lang.Thread!.getClass -> java.lang.Thread!.getClass
        |""".stripMargin
    )
}
