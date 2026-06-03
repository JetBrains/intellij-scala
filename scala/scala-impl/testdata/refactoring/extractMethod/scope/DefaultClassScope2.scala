package org.example

object MyScala {
  def foo(): Unit = {
    val value = 1 + 2
    println(value)
  }

  class MyInner {
    private val field = 42

    def bar1(): Unit = {
      /*start*/val value = 3 + 4
      println(value)/*end*/
    }

    def bar(): Unit = {
      val value = field + field
      println(value)
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
      testMethodName()
    }

    def testMethodName(): Unit = {
      val value = 3 + 4
      println(value)
    }

    def bar(): Unit = {
      val value = field + field
      println(value)
    }
  }
}
*/