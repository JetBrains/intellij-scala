package org.jetbrains.plugins.scala.editor.selectioner

class ScalaDocCommentSelectionerTest extends ExtendWordSelectionHandlerTestBase {
  def testMainContentSelection(): Unit =
    doTest(Seq(
      s"""/**
         | * paragraph 1 line 1
         | * paragraph 1 line 2
         | *
         | * paragraph 2 line 1
         | * ${Caret}paragraph 2 line 2
         | *
         | * paragraph 3 line 1
         | * paragraph 3 line 2
         | */
         |class A
         |""".stripMargin,
      s"""/**
         | * paragraph 1 line 1
         | * paragraph 1 line 2
         | *
         | * paragraph 2 line 1
         | * $Start${Caret}paragraph$End 2 line 2
         | *
         | * paragraph 3 line 1
         | * paragraph 3 line 2
         | */
         |class A
         |""".stripMargin,
      s"""/**
         | * paragraph 1 line 1
         | * paragraph 1 line 2
         | *
         | * paragraph 2 line 1
         | * $Start${Caret}paragraph 2 line 2$End
         | *
         | * paragraph 3 line 1
         | * paragraph 3 line 2
         | */
         |class A
         |""".stripMargin,
      s"""/**
         | * paragraph 1 line 1
         | * paragraph 1 line 2
         | *
         | *$Start paragraph 2 line 1
         | * ${Caret}paragraph 2 line 2
         | *
         | *$End paragraph 3 line 1
         | * paragraph 3 line 2
         | */
         |class A
         |""".stripMargin,
      s"""/**
         | *$Start paragraph 1 line 1
         | * paragraph 1 line 2
         | *
         | * paragraph 2 line 1
         | * ${Caret}paragraph 2 line 2
         | *
         | * paragraph 3 line 1
         | * paragraph 3 line 2
         | $End*/
         |class A
         |""".stripMargin,
      s"""$Start/**
         | * paragraph 1 line 1
         | * paragraph 1 line 2
         | *
         | * paragraph 2 line 1
         | * ${Caret}paragraph 2 line 2
         | *
         | * paragraph 3 line 1
         | * paragraph 3 line 2
         | */
         |${End}class A
         |""".stripMargin,
      s"""$Start/**
         | * paragraph 1 line 1
         | * paragraph 1 line 2
         | *
         | * paragraph 2 line 1
         | * ${Caret}paragraph 2 line 2
         | *
         | * paragraph 3 line 1
         | * paragraph 3 line 2
         | */
         |class A
         |$End""".stripMargin,
    ))

  def testMainContentSelection_WithTags(): Unit =
    doTest(Seq(
      s"""/**
         | * paragraph 1 line 1
         | *
         | * paragraph ${Caret}2 line 1
         | *
         | * paragraph 3 line 1
         | *
         | * @note my note
         | */
         |class A
         |""".stripMargin,
      s"""/**
         | * paragraph 1 line 1
         | *
         | * paragraph $Start${Caret}2$End line 1
         | *
         | * paragraph 3 line 1
         | *
         | * @note my note
         | */
         |class A
         |""".stripMargin,
      s"""/**
         | * paragraph 1 line 1
         | *
         | * ${Start}paragraph ${Caret}2 line 1$End
         | *
         | * paragraph 3 line 1
         | *
         | * @note my note
         | */
         |class A
         |""".stripMargin,
      s"""/**
         | * paragraph 1 line 1
         | *
         | *$Start paragraph ${Caret}2 line 1
         | *
         | *$End paragraph 3 line 1
         | *
         | * @note my note
         | */
         |class A
         |""".stripMargin,
      s"""/**
         | *$Start paragraph 1 line 1
         | *
         | * paragraph ${Caret}2 line 1
         | *
         | * paragraph 3 line 1
         | *
         | *$End @note my note
         | */
         |class A
         |""".stripMargin,
      s"""$Start/**
         | * paragraph 1 line 1
         | *
         | * paragraph ${Caret}2 line 1
         | *
         | * paragraph 3 line 1
         | *
         | * @note my note
         | */
         |${End}class A
         |""".stripMargin,
    ))

  def testTagSelection(): Unit =
    doTest(Seq(
      s"""/**
         | * main content
         | *
         | * @param xxx my ${Caret}param description line 1
         | *            my param description line 2
         | */
         |class A(xxx: Int)
         |""".stripMargin,
      s"""/**
         | * main content
         | *
         | * @param xxx my $Start${Caret}param$End description line 1
         | *            my param description line 2
         | */
         |class A(xxx: Int)
         |""".stripMargin,
      s"""/**
         | * main content
         | *
         | * @param xxx ${Start}my ${Caret}param description line 1$End
         | *            my param description line 2
         | */
         |class A(xxx: Int)
         |""".stripMargin,
      s"""/**
         | * main content
         | *
         | * @param xxx$Start my ${Caret}param description line 1
         | *            my param description line 2
         | $End*/
         |class A(xxx: Int)
         |""".stripMargin,
      s"""/**
         | * main content
         | *
         | *$Start @param xxx my ${Caret}param description line 1
         | *            my param description line 2
         | $End*/
         |class A(xxx: Int)
         |""".stripMargin,
    ))
}