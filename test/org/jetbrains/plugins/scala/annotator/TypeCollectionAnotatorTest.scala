package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.base.{ScalaLightPlatformCodeInsightTestCaseAdapter, TestScalaProjectSettings}
import org.jetbrains.plugins.scala.highlighter.AnnotatorHighlighter
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

/**
 * User: Dmitry Naydanov
 * Date: 3/26/12
 */

class TypeCollectionAnotatorTest extends ScalaLightPlatformCodeInsightTestCaseAdapter with TestScalaProjectSettings {
  private val immutableCollectionMessage = ScalaBundle.message("scala.immutable.collection")
  private val mutableCollectionMessage = ScalaBundle.message("scala.mutable.collection")
  private val javaCollectionMessage = ScalaBundle.message("java.collection")

  protected override def setUp() {
    super.setUp()

    scalaProjectSettings.
      setCollectionTypeHighlightingLevel(ScalaProjectSettings.COLLECTION_TYPE_HIGHLIGHTING_ALL)
  }

  private def annotate(text: String): AnnotatorHolderMock = {
    configureFromFileTextAdapter("dummy.scala", text.replace("\r", ""))

    val holder = new AnnotatorHolderMock(getFileAdapter)

    getFileAdapter.asInstanceOf[ScalaFile].breadthFirst.foreach {
      case refElement: ScReferenceElement => AnnotatorHighlighter.highlightReferenceElement(refElement, holder)
      case _ =>
    }
    holder
  }

  private def testCanAnnotate(text: String, highlightedText: String,  highlightingMessage: String) {
    val holder = annotate(text)

    assert(holder.annotations.exists {
      case Info(`highlightedText`, `highlightingMessage`) => true
      case _ => false
    })
  }

  private def testCannotAnnotate(text: String,  textCantHighlight: (String, String)) {

    val holder = annotate(text)

    assert(!holder.annotations.exists {
      case Info(`textCantHighlight`._1, `textCantHighlight`._2) => true
      case _ => false
    })
  }

  def testAnnotateImmutableSimpple() {
    val text = """
    import scala.collection.immutable.HashMap

    class A {
      val hm = HashMap(1 -> 2)
    }
    """

    testCanAnnotate(text, "HashMap", immutableCollectionMessage)
  }

  def testAnnotateImmutableFromPredef() {
    val text = """
    class A {
      val list = List(1, 2, 3)
    }
    """

    testCanAnnotate(text, "List", immutableCollectionMessage)
  }

  def testAnnotateImmutableWithApplyFromPredef() {
    val text = """
    class A {
      val map = Map.apply(1 -> 2, 3 -> 4)
    }
    """

    testCanAnnotate(text, "Map", immutableCollectionMessage)
  }

  def testAnnotateImmutableQualifiedName() {
    val text = """
    class A {
      val lst = scala.collection.immutable.List(1, 2, 3)
    }
    """

    testCanAnnotate(text, "List", immutableCollectionMessage)
  }

  def testAnnotateImmutableQualifiedNameWithApply() {
    val text = """
    class A {
      val lst = scala.collection.immutable.List.apply(1, 2, 3)
    }
    """

    testCanAnnotate(text, "List", immutableCollectionMessage)
  }

  def testAnnotateImmutableWithExplicitType() {
    val text = """
    class A {
      val lst = Map[String, Int]()
    }
    """

    testCanAnnotate(text, "Map", immutableCollectionMessage)
  }

  def testAnnotateImmutableWithNew() {
    val text = """
    import scala.collection.immutable.HashMap

    class A {
      val hm = new HashMap()
    }
    """

    testCanAnnotate(text, "HashMap", immutableCollectionMessage)
  }

  def testAnnotateImmutableWithTypeAnnotation() {
    val text = """
    import scala.collection.immutable.HashMap

    class A [
      val map: Map[String, String] = HashMap("aaa" -> "bbb")
    }
    """

    testCanAnnotate(text, "Map", immutableCollectionMessage)
  }

  def testAnnotateMutableSimple() {
    val text = """
    import scala.collection.mutable.HashMap

    class A {
      val hm = HashMap()
    }
    """

    testCanAnnotate(text, "HashMap", mutableCollectionMessage)
  }

  def testAnnotateMutableNewFromPredef() {
    val text = """
    class A {
      val sb = new StringBuilder("blah")
    }
    """

    testCanAnnotate(text, "StringBuilder", mutableCollectionMessage)
  }

  def testAnnotateMutableQualifiedName() {
    val text = """
      class A {
        val sb = scala.collection.mutable.HashMap(1 -> 2, 3 -> 4)
      }
    """

    testCanAnnotate(text, "HashMap", mutableCollectionMessage)
  }

  def testAnnotateMutableQualifiedNameWithApply() {
    val text = """
      class A {
        val sb = scala.collection.mutable.HashMap.apply("aaa" -> 2, "3" -> 4)
      }
    """

    testCanAnnotate(text, "HashMap", mutableCollectionMessage)
  }

  def testAnnotateMutableWithExplicitType() {
    val text = """
    import scala.collection.mutable.HashMap

    class A {
      val hm = HashMap[String, Int]()
    }
    """

    testCanAnnotate(text, "HashMap", mutableCollectionMessage)
  }

  def testAnnotateMutableWithTypeAnnotation() {
    val text = """
    import scala.collection.mutable.HashMap
    import collection.mutable.Map

    class A [
      val map: Map[String, String] = HashMap("aaa" -> "bbb")
    }
    """

    testCanAnnotate(text, "Map", mutableCollectionMessage)
  }

  def testAnnotateImmutableAsParam() {
    val text = """
    class A {
      def f(map: Map[String, String]) {

      }
    }
    """

    testCanAnnotate(text, "Map", immutableCollectionMessage)
  }

  def testAnnotateImmutableAsReturnType() {
    val text = """
    import scala.collection.immutable.HashMap

    class A {
      def f(): Map[Int, Int] = {
        HashMap(1 -> 2)
      }
    }
    """

    testCanAnnotate(text, "Map", immutableCollectionMessage)
  }

  def testAnnotateImmutableAsTypeParam() {
    val text = """
    class A[T <: Map[String, String]] {

    }
    """

    testCanAnnotate(text, "Map", immutableCollectionMessage)
  }

  def testAnnotateMutableAsParam() {
    val text = """
    import scala.collection.mutable.Map

    class A {
      def f(map: Map[String, String]) {

      }
    }
    """

    testCanAnnotate(text, "Map", mutableCollectionMessage)
  }

  def testAnnotateMutableAsReturnType() {
    val text = """
    import scala.collection.mutable.Map
    import scala.collection.mutable.HashMap

    class A {
      def f(): Map[Int, Int] = {
        HashMap[Int, Int]()
      }
    }
    """

    testCanAnnotate(text, "Map", mutableCollectionMessage)
  }

  def testAnnotateMutableAsTypeParam() {
    val text = """
    import scala.collection.mutable.Map

    class A[T <: Map[String, String]] {

    }
    """

    testCanAnnotate(text, "Map", mutableCollectionMessage)
  }

  def testAnnotateJavaConstructor() {
    val text = """
    import java.util.ArrayList

    class A {
      val al = new ArrayList[String]()
    }
    """

    testCanAnnotate(text, "ArrayList", javaCollectionMessage)
  }

  def testAnnotateJavaValType() {
    val text = """
    import java.util.ArrayList

    class A {
      val lst: java.util.List[String] = new ArrayList[String]()
    }
    """

    testCanAnnotate(text, "List", javaCollectionMessage)
  }

  def testAnnotateJavaReturnType() {
    val text = """
    import java.util.{ArrayList, List}

    def f(): List[String] = new ArrayList[String]()
    """

    testCanAnnotate(text, "List", javaCollectionMessage)
  }

  def testJavaParamType() {
    val text = """
    import java.util.{ArrayList, List}

    def f(a: List[String]) {
    }
    """

    testCanAnnotate(text, "List", javaCollectionMessage)
  }

  def testCannotAnnotateApply() {
    val text = """
    class A {
      val a = List.apply(1, 2, 3)
    }
    """
    testCannotAnnotate(text, ("apply", immutableCollectionMessage))
  }

  def testCannotHighlightImport() {
    val text = """
      import scala.collection.mutable.HashMap
      import java.util.ArrayList

      class A {

      }
    """

    testCannotAnnotate(text, ("HashMap", mutableCollectionMessage))
    testCannotAnnotate(text, ("ArrayList", javaCollectionMessage))
  }
}
