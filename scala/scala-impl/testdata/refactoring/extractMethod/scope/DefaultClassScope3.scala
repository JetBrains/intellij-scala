package org.example

object MyScala {
  def foo(): Unit = {
    val value = 1 + 2
    println(value)
  }

  class MyInner {
    private val field = 42

    def bar1(): Unit = {
      val value = 3 + 4
      println(value)
    }

    def bar(): Unit = {
      /*start*/val value = field + field
      println(value)/*end*/
    }
  }
}
/*
package org.example

object MyScala {
  def foo(): Unit = {
    val value = 1 + 2
    println(value)
  }

  class MyInner {
    private val field = 42

    def bar1(): Unit = {
      val value = 3 + 4
      println(value)
    }

    def bar(): Unit = {
      testMethodName()
    }

    def testMethodName(): Unit = {
      val value = field + field
      println(value)
    }
  }
}
*/