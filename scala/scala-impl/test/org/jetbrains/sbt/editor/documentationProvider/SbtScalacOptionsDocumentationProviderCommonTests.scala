package org.jetbrains.sbt.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.util.text.HtmlChunk
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.sbt.language.psi.SbtScalacOptionDocHolder
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo
import org.jetbrains.sbt.language.utils.SbtScalacOptionInfo.ArgType

trait SbtScalacOptionsDocumentationProviderCommonTests {
  self: SbtScalacOptionsDocumentationProviderTestBase =>

  private val NONEXISTENT_FLAG = "-flag-that-no-one-should-ever-add-to-compiler"
  private val DEPRECATION_FLAG = "-deprecation"

  private def expectedDocumentation(langLevel: ScalaLanguageLevel, flag: String, description: String,
                                    choices: Set[String] = Set.empty, defaultValue: Option[String] = None): String = {
    def section(name: String, content: String) =
      HtmlChunk.tag("tr").children(
        DocumentationMarkup.SECTION_HEADER_CELL.child(HtmlChunk.p().addText(name)),
        DocumentationMarkup.SECTION_CONTENT_CELL.addText(content)
      )

    val descriptionSection = Option(section("Description", description))
    val choicesSection =
      Option.when(choices.nonEmpty)(section("Choices", choices.toList.sorted.mkString("[", ", ", "]")))
    val defaultValueSection = defaultValue.map(section("Default value", _))
    val sections = Seq(descriptionSection, choicesSection, defaultValueSection).flatten

    val definition = DocumentationMarkup.DEFINITION_ELEMENT.child(HtmlChunk.tag("pre").child(HtmlChunk.text(flag).bold()))
    val content = DocumentationMarkup.CONTENT_ELEMENT.children(
      HtmlChunk.text(langLevel.getVersion),
      DocumentationMarkup.SECTIONS_TABLE.children(sections: _*),
      HtmlChunk.br()
    )

    definition.toString + content.toString
  }

  private lazy val DEPRECATION_DOC = {
    import ScalaLanguageLevel._
    val langLevel = getVersion.languageLevel

    val description = langLevel match {
      case Scala_2_11 | Scala_3_0 | Scala_3_1 =>
        "Emit warning and location for usages of deprecated APIs."
      case Scala_2_12 | Scala_2_13 =>
        "Emit warning and location for usages of deprecated APIs. See also -Wconf. [false]"
      case _ => throw new IllegalStateException(s"Unexpected language level: ${langLevel.getVersion}")
    }

    expectedDocumentation(langLevel, DEPRECATION_FLAG, description)
  }

  private def getVersion(implicit ev: ScalaVersion): ScalaVersion = ev

  def test_topLevel_single(): Unit = doGenerateDocTest(
    s"""scalacOptions += "${|}$DEPRECATION_FLAG"""",
    DEPRECATION_DOC
  )

  def test_topLevel_seq(): Unit = doGenerateDocTest(
    s"""scalacOptions ++= Seq("${|}$DEPRECATION_FLAG")""",
    DEPRECATION_DOC
  )

  def test_topLevel_complexExpression_seq(): Unit = doGenerateDocTest(
    s"""scalacOptions ++= {
       |  if (1 == 2) {
       |    Nil
       |  } else Seq("${|}$DEPRECATION_FLAG")
       |}""".stripMargin,
    DEPRECATION_DOC
  )

  def test_inProjectSettings_single(): Unit = doGenerateDocTest(
    s"""
       |lazy val foo = project.in(file("foo"))
       |  .settings(
       |    name := "foo",
       |    scalaVersion := "$getVersion",
       |    scalacOptions += "${|}$DEPRECATION_FLAG"
       |  )
       |""".stripMargin,
    DEPRECATION_DOC
  )

  def test_inProjectSettings_seq(): Unit = doGenerateDocTest(
    s"""
       |lazy val foo = project.in(file("foo"))
       |  .settings(
       |    name := "foo",
       |    scalaVersion := "$getVersion",
       |    scalacOptions ++= Seq("${|}$DEPRECATION_FLAG")
       |  )
       |""".stripMargin,
    DEPRECATION_DOC
  )

  def test_inProjectSettings_complexExpression_seq(): Unit = doGenerateDocTest(
    s"""
       |lazy val foo = project.in(file("foo"))
       |  .settings(
       |    name := "foo",
       |    scalaVersion := "$getVersion",
       |    scalacOptions ++= {
       |      if (1 == 2) {
       |        Nil
       |      } else Seq("${|}$DEPRECATION_FLAG")
       |    }
       |  )
       |""".stripMargin,
    DEPRECATION_DOC
  )

  def test_topLevel_single_notFound(): Unit = doGenerateDocTest(
    s"""scalacOptions += "${|}$NONEXISTENT_FLAG"""",
    null
  )

  def test_topLevel_seq_notFound(): Unit = doGenerateDocTest(
    s"""scalacOptions ++= Seq("${|}$NONEXISTENT_FLAG")""",
    null
  )

  def test_inProjectSettings_single_notFound(): Unit = doGenerateDocTest(
    s"""
       |lazy val foo = project.in(file("foo"))
       |  .settings(
       |    name := "foo",
       |    scalaVersion := "$getVersion",
       |    scalacOptions += "${|}$NONEXISTENT_FLAG"
       |  )
       |""".stripMargin,
    null
  )

  def test_inProjectSettings_seq_notFound(): Unit = doGenerateDocTest(
    s"""
       |lazy val foo = project.in(file("foo"))
       |  .settings(
       |    name := "foo",
       |    scalaVersion := "$getVersion",
       |    scalacOptions ++= Seq("${|}$NONEXISTENT_FLAG")
       |  )
       |""".stripMargin,
    null
  )

  def test_topLevel_single_notScalacOptions_notFound(): Unit = doGenerateDocTest(
    s"""javacOptions += "${|}$DEPRECATION_FLAG"""",
    null
  )

  def test_topLevel_seq_notScalacOptions_notFound(): Unit = doGenerateDocTest(
    s"""javacOptions ++= Seq("${|}$DEPRECATION_FLAG")""",
    null
  )

  def test_inProjectSettings_single_notScalacOptions_notFound(): Unit = doGenerateDocTest(
    s"""
       |lazy val foo = project.in(file("foo"))
       |  .settings(
       |    name := "foo",
       |    scalaVersion := "$getVersion",
       |    javacOptions += "${|}$DEPRECATION_FLAG"
       |  )
       |""".stripMargin,
    null
  )

  def test_inProjectSettings_seq_notScalacOptions_notFound(): Unit = doGenerateDocTest(
    s"""
       |lazy val foo = project.in(file("foo"))
       |  .settings(
       |    name := "foo",
       |    scalaVersion := "$getVersion",
       |    javacOptions ++= Seq("${|}$DEPRECATION_FLAG")
       |  )
       |""".stripMargin,
    null
  )

  def test_lookupElement(): Unit = {
    val langLevel = version.languageLevel
    val flag = "-test-flag"
    val description = "Scalac options lookup element documentation test description"
    val descriptions = Map(langLevel -> description)
    val defaultValue = Some("default test choice")
    val choices = Map(langLevel -> Set(defaultValue.get, "test choice", "another test choice"))
    val option = SbtScalacOptionInfo(flag, descriptions, choices, ArgType.No, Set(langLevel), defaultValue)
    val docHolder = new SbtScalacOptionDocHolder(option)(self.getFixture.getProject)

    val expectedDoc = expectedDocumentation(langLevel, flag, description, choices(langLevel), defaultValue)
    val actualDoc = generateDoc(docHolder, null)
    assertDocHtml(expectedDoc, actualDoc)
  }

}
