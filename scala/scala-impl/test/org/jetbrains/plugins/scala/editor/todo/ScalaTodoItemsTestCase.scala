package org.jetbrains.plugins.scala.editor.todo

/** tests [[ScalaIndexPatternBuilder]] */
class ScalaTodoItemsTestCase extends ScalaTodoItemsTestCaseBase {

  def testTodo_LineComment(): Unit = doTodoTest(
    s"""// ${start}TODO: do something$end
       |// unrelated comment line
       |val x =  42
       |""".stripMargin
  )

  def testTodo_LineComment_1(): Unit = doTodoTest(
    s"""// ${start}TODO: do something$end
       |
       |//  unrelated comment line
       |val x =  42
       |""".stripMargin
  )

  def testTodo_LineComment_MultilineTodo(): Unit = doTodoTest(
    s"""// ${start}TODO: do something$end
       |//  ${start}todo description continue$end
       |val x =  42
       |""".stripMargin
  )

  def testTodo_BlockComment(): Unit = doTodoTest(
    s"""/*
       | * ${start}TODO: do something$end
       | * unrelated comment line
       | */
       |val x =  42
       |""".stripMargin
  )

  def testTodo_BlockComment_1(): Unit = doTodoTest(
    s"""/*
       | * ${start}TODO: do something$end
       | *
       | *  unrelated comment line
       | */
       |val x =  42
       |""".stripMargin
  )

  def testTodo_BlockComment_MultilineTodo(): Unit = doTodoTest(
    s"""/*
       | * ${start}TODO: do something$end
       | *  ${start}todo description continue$end
       | */
       |val x =  42
       |""".stripMargin
  )

  def testTodo_ScaladocComment(): Unit = doTodoTest(
    s"""/**
       | * ${start}TODO: do something$end
       | * unrelated comment line
       | */
       |val x =  42
       |""".stripMargin
  )

  def testTodo_ScaladocComment_1(): Unit = doTodoTest(
    s"""/**
       | * ${start}TODO: do something$end
       | *
       | *  unrelated comment line
       | */
       |val x =  42
       |""".stripMargin
  )

  def testTodo_ScaladocComment_AndSomeTodoOfAnotherType(): Unit = doTodoTest(
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

  def testTodo_ScaladocComment_MultilineTodo(): Unit = doTodoTest(
    s"""/**
       | * ${start}TODO: do something$end
       | *  ${start}todo description continue$end
       | */
       |val x =  42
       |""".stripMargin
  )

  def testTodo_ScaladocComment_WithOtherFields(): Unit = doTodoTest(
    s"""/**
       | * ${start}TODO: do something$end
       | * @param x some description
       | * @returs something
       | */
       |def foo(x: String) = 42
       |""".stripMargin
  )

  def testTodo_ScaladocComment_WithOtherFields_MultilineTodo(): Unit = doTodoTest(
    s"""/**
       | * ${start}TODO: do something$end
       | *  ${start}todo description continue$end
       | * @param x some description
       | * @returs something
       | */
       |def foo(x: String) = 42
       |""".stripMargin
  )

  def testTodo_ScaladocComment_TagTodo(): Unit = doTodoTest(
    s"""/**
       | * @param x some description
       | $start@todo do something$end
       | * @returs something
       | */
       |def foo(x: String) = 42
       |""".stripMargin
  )
}