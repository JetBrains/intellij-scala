package scala.meta.annotations

import org.jetbrains.plugins.scala.SlowTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
class MetaAnnotationUndoExpansionTest extends MetaAnnotationUndoExpansionTestBase {

  def testUndoSingleClass(): Unit = {
    val annotText =
      s"""
         |q"class $testClassName { def foo = 42 }"
      """.stripMargin

    val testFileText =
      s"""
         |@$annotName
         |class $testClassName
      """.stripMargin

    checkUndo(annotText, testFileText)
  }

  def testUndoCompanionForClass(): Unit = {
    val annotText =
      s"""
         |q$tq
         |class $testClassName  { def foo = 42 }
         |object $testClassName  { def bar = 42 }
         |$tq
       """.stripMargin

    val fileText =
      s"""
         |@$annotName
         |class $testClassName
      """.stripMargin

    checkUndo(annotText, fileText)
  }

  def testUndoModifyCompanion(): Unit = {
    val annotText =
      s"""
         |q$tq
         |class $testClassName  { def foo = 42 }
         |object $testClassName  { def bar = 42 }
         |$tq
       """.stripMargin

    val fileText =
      s"""
         |@$annotName
         |class $testClassName
         |
         |object $testClassName
      """.stripMargin

    checkUndo(annotText, fileText)
  }
}
