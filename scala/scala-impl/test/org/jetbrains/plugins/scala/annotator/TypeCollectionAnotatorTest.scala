package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.{ScalaLightPlatformCodeInsightTestCaseAdapter, TestScalaProjectSettings}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.highlighter.ScalaColorSchemeAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings.ScalaCollectionHighlightingLevel

import scala.annotation.nowarn

@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
class TypeCollectionAnotatorTest extends ScalaLightPlatformCodeInsightTestCaseAdapter with TestScalaProjectSettings {
  private val immutableCollectionMessage = ScalaBundle.message("scala.immutable.collection")
  private val mutableCollectionMessage = ScalaBundle.message("scala.mutable.collection")
  private val javaCollectionMessage = ScalaBundle.message("java.collection")

  protected override def setUp(): Unit = {
    super.setUp()

    scalaProjectSettings.setCollectionTypeHighlightingLevel(ScalaCollectionHighlightingLevel.All)
  }

  private def annotate(text: String): AnnotatorHolderMock = {
    configureFromFileTextAdapter("dummy.scala", text.replace("\r", ""))

    val holder = new AnnotatorHolderMock(getFileAdapter)

    getFileAdapter.asInstanceOf[ScalaFile].breadthFirst().foreach {
      case refElement: ScReference => ScalaColorSchemeAnnotator.highlightReferenceElement(refElement)(holder)
      case _ =>
    }
    holder
  }

  private def testCanAnnotate(text: String, highlightedText: String,  highlightingMessage: String): Unit = {
    val holder = annotate(text)

    assert(holder.annotations.exists {
      case Info(`highlightedText`, `highlightingMessage`) => true
      case _ => false
    })
  }

  private def testCannotAnnotate(text: String,  textCantHighlight: (String, String)): Unit = {

    val holder = annotate(text)

    assert(!holder.annotations.exists {
      case Info(`textCantHighlight`._1, `textCantHighlight`._2) => true
      case _ => false
    })
  }

  def testAnnotateImmutableSimpple(): Unit = {
    val text = """
    import scala.collection.immutable.HashMap

    class A {
      val hm = HashMap(1 -> 2)
    }
    """

    testCanAnnotate(text, "HashMap", immutableCollectionMessage)
  }

  def testAnnotateImmutableFromPredef(): Unit = {
    val text = """
    class A {
      val list = List(1, 2, 3)
    }
    """

    testCanAnnotate(text, "List", immutableCollectionMessage)
  }

  def testAnnotateImmutableWithApplyFromPredef(): Unit = {
    val text = """
    class A {
      val map = Map.apply(1 -> 2, 3 -> 4)
    }
    """

    testCanAnnotate(text, "Map", immutableCollectionMessage)
  }

  def testAnnotateImmutableQualifiedName(): Unit = {
    val text = """
    class A {
      val lst = scala.collection.immutable.List(1, 2, 3)
    }
    """

    testCanAnnotate(text, "List", immutableCollectionMessage)
  }

  def testAnnotateImmutableQualifiedNameWithApply(): Unit = {
    val text = """
    class A {
      val lst = scala.collection.immutable.List.apply(1, 2, 3)
    }
    """

    testCanAnnotate(text, "List", immutableCollectionMessage)
  }

  def testAnnotateImmutableWithExplicitType(): Unit = {
    val text = """
    class A {
      val lst = Map[String, Int]()
    }
    """

    testCanAnnotate(text, "Map", immutableCollectionMessage)
  }

  def testAnnotateImmutableWithNew(): Unit = {
    val text = """
    import scala.collection.immutable.HashMap

    class A {
      val hm = new HashMap()
    }
    """

    testCanAnnotate(text, "HashMap", immutableCollectionMessage)
  }

  def testAnnotateImmutableWithTypeAnnotation(): Unit = {
    val text = """
    import scala.collection.immutable.HashMap

    class A [
      val map: Map[String, String] = HashMap("aaa" -> "bbb")
    }
    """

    testCanAnnotate(text, "Map", immutableCollectionMessage)
  }

  def testAnnotateMutableSimple(): Unit = {
    val text = """
    import scala.collection.mutable.HashMap

    class A {
      val hm = HashMap()
    }
    """

    testCanAnnotate(text, "HashMap", mutableCollectionMessage)
  }

  def testAnnotateMutableNewFromPredef(): Unit = {
    val text = """
    class A {
      val sb = new StringBuilder("blah")
    }
    """

    testCanAnnotate(text, "StringBuilder", mutableCollectionMessage)
  }

  def testAnnotateMutableQualifiedName(): Unit = {
    val text = """
      class A {
        val sb = scala.collection.mutable.HashMap(1 -> 2, 3 -> 4)
      }
    """

    testCanAnnotate(text, "HashMap", mutableCollectionMessage)
  }

  def testAnnotateMutableQualifiedNameWithApply(): Unit = {
    val text = """
      class A {
        val sb = scala.collection.mutable.HashMap.apply("aaa" -> 2, "3" -> 4)
      }
    """

    testCanAnnotate(text, "HashMap", mutableCollectionMessage)
  }

  def testAnnotateMutableWithExplicitType(): Unit = {
    val text = """
    import scala.collection.mutable.HashMap

    class A {
      val hm = HashMap[String, Int]()
    }
    """

    testCanAnnotate(text, "HashMap", mutableCollectionMessage)
  }

  def testAnnotateMutableWithTypeAnnotation(): Unit = {
    val text = """
    import scala.collection.mutable.HashMap
    import collection.mutable.Map

    class A [
      val map: Map[String, String] = HashMap("aaa" -> "bbb")
    }
    """

    testCanAnnotate(text, "Map", mutableCollectionMessage)
  }

  def testAnnotateImmutableAsParam(): Unit = {
    val text = """
    class A {
      def f(map: Map[String, String]) {

      }
    }
    """

    testCanAnnotate(text, "Map", immutableCollectionMessage)
  }

  def testAnnotateImmutableAsReturnType(): Unit = {
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

  def testAnnotateImmutableAsTypeParam(): Unit = {
    val text = """
    class A[T <: Map[String, String]] {

    }
    """

    testCanAnnotate(text, "Map", immutableCollectionMessage)
  }

  def testAnnotateMutableAsParam(): Unit = {
    val text = """
    import scala.collection.mutable.Map

    class A {
      def f(map: Map[String, String]) {

      }
    }
    """

    testCanAnnotate(text, "Map", mutableCollectionMessage)
  }

  def testAnnotateMutableAsReturnType(): Unit = {
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

  def testAnnotateMutableAsTypeParam(): Unit = {
    val text = """
    import scala.collection.mutable.Map

    class A[T <: Map[String, String]] {

    }
    """

    testCanAnnotate(text, "Map", mutableCollectionMessage)
  }

  def testAnnotateJavaConstructor(): Unit = {
    val text = """
    import java.util.ArrayList

    class A {
      val al = new ArrayList[String]()
    }
    """

    testCanAnnotate(text, "ArrayList", javaCollectionMessage)
  }

  def testAnnotateJavaValType(): Unit = {
    val text = """
    import java.util.ArrayList

    class A {
      val lst: java.util.List[String] = new ArrayList[String]()
    }
    """

    testCanAnnotate(text, "List", javaCollectionMessage)
  }

  def testAnnotateJavaReturnType(): Unit = {
    val text = """
    import java.util.{ArrayList, List}

    def f(): List[String] = new ArrayList[String]()
    """

    testCanAnnotate(text, "List", javaCollectionMessage)
  }

  def testJavaParamType(): Unit = {
    val text = """
    import java.util.{ArrayList, List}

    def f(a: List[String]) {
    }
    """

    testCanAnnotate(text, "List", javaCollectionMessage)
  }

  def testCannotAnnotateApply(): Unit = {
    val text = """
    class A {
      val a = List.apply(1, 2, 3)
    }
    """
    testCannotAnnotate(text, ("apply", immutableCollectionMessage))
  }

  def testCannotHighlightImport(): Unit = {
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
