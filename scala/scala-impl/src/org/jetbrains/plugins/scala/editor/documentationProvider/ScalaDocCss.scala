package org.jetbrains.plugins.scala.editor.documentationProvider

private object ScalaDocCss {

  /**
   * After discussing it Dmitrii Batrak from IntelliJ core team, we concluded that for now the only normal way
   * to provide some custom CSS styles is directly via <head><style>...</style><head> in <html> tag.
   *
   * @see [[com.intellij.util.ui.JBHtmlEditorKit]]
   * @see [[com.intellij.codeInsight.documentation.DocumentationComponent.prepareCSS]]
   * @see platform-impl.jar!/com/intellij/ide/ui/laf/intellijlaf.css
   * @see platform-impl.jar!/com/intellij/ide/ui/laf/darcula/darcula.css
   * @see IDEA-243159, IDEA-229463
   */
  //noinspection HtmlRequiredTitleElement,CssUnknownProperty
  //language=CSS
  val  value: String =
    """/*
      | * NOTE: margin for list items is calculated relatively to list item content, so if margin=0, list head element
      | * will be rendered to the left of the main content. List item head is ~5px.
      | * So first list requires a little bit larger margin the nested lists.
      | */
      |ol, ul {
      |  margin-left-ltr: 20px;
      |  margin-top: 0;
      |}
      |
      |li ol, li ul {
      |  margin-left-ltr: 15px;
      |  margin-top: 0;
      |  margin-bottom: 0;
      |  padding-bottom: 0;
      |}
      |
      |li {
      |  margin-left-ltr: 0;
      |  margin-left: 0;
      |  margin-bottom: 0;
      |  padding-bottom: 0;
      |}
      |
      |ul { list-style-type: disc; }
      |ul li ul { list-style-type: circle; }
      |ul li ul li ul { list-style-type: square; }
      |
      |ol .decimal    { list-style-type: decimal; }
      |ol .upperRoman { list-style-type: upper-roman; }
      |ol .lowerRoman { list-style-type: lower-roman; }
      |ol .upperAlpha { list-style-type: upper-alpha; }
      |ol .lowerAlpha { list-style-type: lower-alpha; }
      |
      |h1, h2, h3, h4, h5, h6 {
      |    font-weight: bold;
      |    margin-top: 10px;
      |    margin-bottom: 5px;
      |}
      |h1 { font-size: x-large; }
      |h2 { font-size: large; }
      |h3 { font-size: medium; }
      |h4 { font-size: small; }
      |h5 { font-size: x-small; }
      |h6 { font-size: xx-small; }
      |""".stripMargin
}
