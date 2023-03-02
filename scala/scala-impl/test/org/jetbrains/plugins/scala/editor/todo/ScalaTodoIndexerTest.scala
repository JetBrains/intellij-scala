package org.jetbrains.plugins.scala.editor.todo

import org.jetbrains.sbt.SbtHighlightingUtil

/** tests [[ScalaIndexPatternBuilder]] */
class ScalaTodoIndexerTest extends ScalaTodoItemsTestBase {

  override protected def setUp(): Unit = {
    super.setUp()
    //we have a test for sbt file
    SbtHighlightingUtil.enableHighlightingOutsideBuildModule(getProject)
  }

  def testTodo_LineComment(): Unit = testTodos(
    s"""// ${start}TODO: do something$end
       |// unrelated comment line
       |val x =  42
       |""".stripMargin
  )

  def testTodo_LineComment_1(): Unit = testTodos(
    s"""// ${start}TODO: do something$end
       |
       |//  unrelated comment line
       |val x =  42
       |""".stripMargin
  )

  def testTodo_LineComment_MultilineTodo(): Unit = testTodos(
    s"""// ${start}TODO: do something$end
       |//  ${start}todo description continue$end
       |val x =  42
       |""".stripMargin
  )

  def testTodo_BlockComment(): Unit = testTodos(
    s"""/*
       | * ${start}TODO: do something$end
       | * unrelated comment line
       | */
       |val x =  42
       |""".stripMargin
  )

  def testTodo_BlockComment_1(): Unit = testTodos(
    s"""/*
       | * ${start}TODO: do something$end
       | *
       | *  unrelated comment line
       | */
       |val x =  42
       |""".stripMargin
  )

  def testTodo_BlockComment_MultilineTodo(): Unit = testTodos(
    s"""/*
       | * ${start}TODO: do something$end
       | *  ${start}todo description continue$end
       | */
       |val x =  42
       |""".stripMargin
  )

  def testTodo_ScaladocComment(): Unit = testTodos(
    s"""/**
       | * ${start}TODO: do something$end
       | * unrelated comment line
       | */
       |val x =  42
       |""".stripMargin
  )

  def testTodo_ScaladocComment_1(): Unit = testTodos(
    s"""/**
       | * ${start}TODO: do something$end
       | *
       | *  unrelated comment line
       | */
       |val x =  42
       |""".stripMargin
  )

  def testTodo_ScaladocComment_AndSomeTodoOfAnotherType(): Unit = testTodos(
    s"""/**
       | * ${start}TODO: do something$end
       | *
       | *  unrelated comment line
       | */
       |val x =  42
       |
       |//${start}TODO: do somthing else$end
       |""".stripMargin
  )

  def testTodo_ScaladocComment_MultilineTodo(): Unit = testTodos(
    s"""/**
       | * ${start}TODO: do something$end
       | *  ${start}todo description continue$end
       | */
       |val x =  42
       |""".stripMargin
  )

  def testTodo_ScaladocComment_WithOtherFields(): Unit = testTodos(
    s"""/**
       | * ${start}TODO: do something$end
       | * @param x some description
       | * @returs something
       | */
       |def foo(x: String) = 42
       |""".stripMargin
  )

  def testTodo_ScaladocComment_WithOtherFields_MultilineTodo(): Unit = testTodos(
    s"""/**
       | * ${start}TODO: do something$end
       | *  ${start}todo description continue$end
       | * @param x some description
       | * @returs something
       | */
       |def foo(x: String) = 42
       |""".stripMargin
  )

  def testTodo_ScaladocComment_TagTodo(): Unit = testTodos(
    s"""/**
       | * @param x some description
       | * @${start}todo do something$end
       | * @returns something
       | */
       |def foo(x: String) = 42
       |""".stripMargin
  )

  def testTodo_ScaladocComment_InWorksheet_HealthCheck(): Unit = testTodos(
    s"""/**
       | * ${start}TODO: do something$end
       | * unrelated comment line
       | */
       |val x =  42
       |""".stripMargin,
    fileType = "sc"
  )

  def testTodo_ScaladocComment_InSbt_HealthCheck(): Unit = testTodos(
    s"""/**
       | * ${start}TODO: do something$end
       | * unrelated comment line
       | */
       |val x =  42
       |""".stripMargin,
    fileType = "sbt"
  )
}