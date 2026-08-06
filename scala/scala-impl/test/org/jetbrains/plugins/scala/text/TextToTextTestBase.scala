package org.jetbrains.plugins.scala.text

import com.intellij.psi.PsiPackage
import com.intellij.testFramework.TestLoggerKt
import com.intellij.util.AstLoadingFilter
import org.jetbrains.plugins.scala.corpus.ProjectCorpusTestBase
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}
import org.jetbrains.plugins.scala.text.TextToTextTestBase._
import org.junit.{Assert, Test}

import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * https://youtrack.jetbrains.com/issue/SCL-24777/Decompiler-tests-documentation
 *
 * https://youtrack.jetbrains.com/issue/SCL-21078/Text-to-text-tests-for-Scala-libraries
 * https://youtrack.jetbrains.com/issue/SCL-23331/Compiler-to-decompiler-tests
 *
 * Implementation: [[org.jetbrains.plugins.scala.decompiler.scalasig.ScalaSigPrinter]]
 * Unit tests:     [[org.jetbrains.plugins.scala.decompiler.DecompilerTest2]]
 *
 * Implementation: [[org.jetbrains.plugins.scala.tasty.reader.TreePrinter]]
 * Unit tests:     [[org.jetbrains.plugins.scala.tasty.reader.DecompilerTest3]]
 */
trait TextToTextTestBase extends ProjectCorpusTestBase {
  private val config: TextToTextTestConfig = TextToTextTestConfig.projectConfigs(projectDef)
  import config._

  @Test
  def textToText(): Unit = {
    ScalaApplicationSettings.PRECISE_TEXT = true
    try {
      doTestTextToText()
    } finally {
      ScalaApplicationSettings.PRECISE_TEXT = false
    }
  }

  private def withAstLoadingFilter[A](block: => A): A = {
    if (astLoadingFilter) {
      AstLoadingFilter.disallowTreeLoading { () =>
        return block
      }
    } else {
      block
    }
  }

  private def doTestTextToText(): Unit = {
    val manager = ScalaPsiManager.instance(getProject)

    println("Collecting classes...")

    val classes = allClasses(packageExceptions)
      .filter(cls => if (version.isScala3) cls.isInScala3File else !cls.isInScala3File)

    val total = classes.length

    Assert.assertTrue(s"Too few classes: $total < $minClassCount", total >= minClassCount)
    Assert.assertTrue(s"Too many classes: $total > 1.1 * $minClassCount", total < 1.1D * minClassCount)

    Assert.assertEquals("Class not found", Set.empty, classExceptions -- classes.map(_.qualifiedName).toSet)

    println(s"Testing $total classes:")

    classes.zipWithIndex.foreach { case (cls, i) =>
      println(f"${i + 1}%04d/$total%s: ${cls.qualifiedName}")

      Assert.assertTrue("Must be in a compiled file: ${cls.qualifiedName}", cls.isInCompiledFile)

      val stub = {
        val text = withAstLoadingFilter(textOfCompilationUnit(cls, withPrivate = true, normalize = false))
        val errors = TestLoggerKt.getErrorLog.takeLoggedErrors()
        val s = if (errors.isEmpty) text else errors.asScala.map(_.toString).mkString("\n")
        transformed(Content.Stub, s)
      }

      val decompiled = {
        val s1 = cls.getContainingFile.getText
        // Function type by-name parameters, SCL-21149
        val s2 = if (cls.qualifiedName.startsWith("scalaz.")) s1.replace("(=> ", "(").replace(", => ", ", ").replaceAll("\\((\\S+)\\) => ", "$1 => ") else s1
        s2.replaceAll("\\.super\\[.*?\\*/\\]\\.", ".this.")
      }

      val decompiledVsStub = transformed(Content.DecompiledVsStub, decompiled)

      if (classExceptions(cls.qualifiedName)) {
        Assert.assertNotEquals(s"Expected to contain errors: ${cls.qualifiedName}", decompiledVsStub, stub)
      } else {
        Assert.assertEquals(s"${cls.qualifiedName} [decompiled | stub]", decompiledVsStub, stub)
//        if (decompiledVsStub != stub) Console.err.println(s"\"${cls.qualifiedName}\", //")

        if (withSources && !classesWithoutSource(cls.qualifiedName)) {
          val sourceCls = cls.getSourceMirrorClass.asInstanceOf[ScTypeDefinition]
          Assert.assertTrue(s"Must have a source: ${cls.qualifiedName}", sourceCls != cls)
          Assert.assertFalse(s"Must be in a source file: ${cls.qualifiedName}", sourceCls.isInCompiledFile)

          val decompiledVsSourceOutline = aliased(transformed(Content.DecompiledVsSourceOutline, decompiled))

          val sourceOutline = {
            val s = textOfCompilationUnit(sourceCls, withPrivate = false, normalize = true)
            aliased(transformed(Content.SourceOutline, s))
          }

          // TODO Remove the exception when the ^ syntax in Scala 3.8 is parsed correctly
          if (!(version.isScala3 && includeScalaLibrarySources && projectDef.packages == Seq("scala") && sourceCls.getContainingFile.textContains('^'))) {
            if (sourceExceptions(cls.qualifiedName)) {
              Assert.assertNotEquals(s"Expected to contain errors: ${cls.qualifiedName}", decompiledVsSourceOutline, sourceOutline)
            } else {
              Assert.assertEquals(s"${cls.qualifiedName} [decompiled | sourceOutline]", decompiledVsSourceOutline, sourceOutline)
//              if (decompiledVsSourceOutline != sourceOutline) Console.err.println(s"\"${cls.qualifiedName}\", //")
            }
          }
        }
      }
    }

    println("Done.")
  }

  private def textOfCompilationUnit(cls: ScTypeDefinition, withPrivate: Boolean, normalize: Boolean): String = {
    val packageName = cls.qualifiedName.substring(0, cls.qualifiedName.lastIndexOf('.'))

    val companionTypeAlias = ScalaPsiManager.instance(cls.getProject).getTopLevelDefinitionsByPackage(packageName, cls.getResolveScope).collect {
      case a: ScTypeAlias if a.name == cls.name => a
    }

    val sb = new StringBuilder()

    sb ++= "package " + packageName + "\n"

    val printer = new ClassPrinter(version.isScala3, withPrivate = withPrivate, normalize = normalize)
    companionTypeAlias.foreach(printer.printTo(sb, _))
    printer.printTo(sb, cls)
    cls.baseCompanionTypeDefinition.foreach(printer.printTo(sb, _))

    sb.setLength(sb.length - 1)

    sb.toString
  }

  private def aliased(text: String): String = {
    val s = if (aliasJava) aliasedJava(text) else text
    if (aliasScala) aliasedScala(s) else s
  }
}

private object TextToTextTestBase {
  sealed abstract class Content extends Product with Serializable
  object Content {
    case object DecompiledVsStub extends Content
    case object Stub extends Content
    case object DecompiledVsSourceOutline extends Content
    case object SourceOutline extends Content
  }

  private def aliasedJava(text: String): String = text
      .replace("_root_.java.lang.String", "_root_.scala.Predef.String")
      .replace("_root_.java.lang.Class", "_root_.scala.Predef.Class")
      .replace("_root_.java.lang.Cloneable", "_root_.scala.Cloneable")
      .replace("_root_.java.lang.Serializable", "_root_.scala.Serializable")
      .replace("_root_.java.lang.Throwable", "_root_.scala.Throwable")
      .replace("_root_.java.lang.Exception", "_root_.scala.Exception")
      .replace("_root_.java.lang.Error", "_root_.scala.Error")
      .replace("_root_.java.lang.RuntimeException", "_root_.scala.RuntimeException")
      .replace("_root_.java.lang.NullPointerException", "_root_.scala.NullPointerException")
      .replace("_root_.java.lang.ClassCastException", "_root_.scala.ClassCastException")
      .replace("_root_.java.lang.IndexOutOfBoundsException", "_root_.scala.IndexOutOfBoundsException")
      .replace("_root_.java.lang.ArrayIndexOutOfBoundsException", "_root_.scala.ArrayIndexOutOfBoundsException")
      .replace("_root_.java.lang.StringIndexOutOfBoundsException", "_root_.scala.StringIndexOutOfBoundsException")
      .replace("_root_.java.lang.UnsupportedOperationException", "_root_.scala.UnsupportedOperationException")
      .replace("_root_.java.lang.IllegalArgumentException", "_root_.scala.IllegalArgumentException")
      .replace("_root_.java.lang.NoSuchElementException", "_root_.scala.NoSuchElementException")
      .replace("_root_.java.lang.NumberFormatException", "_root_.scala.NumberFormatException")
      .replace("_root_.java.lang.AbstractMethodError", "_root_.scala.AbstractMethodError")
      .replace("_root_.java.lang.InterruptedException", "_root_.scala.InterruptedException")

  private def aliasedScala(text: String): String = text
      .replace("_root_.scala.collection.immutable.Map", "_root_.scala.Predef.Map")
      .replace("_root_.scala.collection.immutable.Set", "_root_.scala.Predef.Set")
      .replace("_root_.scala.collection.Iterable", "_root_.scala.Iterable")
      .replace("_root_.scala.collection.immutable.Seq", "_root_.scala.Seq")
      .replace("_root_.scala.collection.immutable.IndexedSeq", "_root_.scala.IndexedSeq")
      .replace("_root_.scala.collection.immutable.List", "_root_.scala.List")
      .replace("_root_.scala.collection.immutable.Nil", "_root_.scala.Nil")
      .replace("_root_.scala.collection.immutable.::", "_root_.scala.::")
      .replace("_root_.scala.collection.immutable.:+", "_root_.scala.:+")
      .replace("_root_.scala.collection.immutable.+:", "_root_.scala.+:")
      .replace("_root_.scala.collection.Iterator", "_root_.scala.Iterator")
      .replace("_root_.scala.collection.BufferedIterator", "_root_.scala.BufferedIterator")
      .replace("_root_.scala.collection.immutable.Stream", "_root_.scala.Stream")
      .replace("_root_.scala.collection.immutable.LazyList", "_root_.scala.LazyList")
      .replace("_root_.scala.collection.mutable.StringBuilder", "_root_.scala.StringBuilder")
      .replace("_root_.scala.collection.immutable.Vector", "_root_.scala.Vector")
      .replace("_root_.scala.collection.immutable.Range", "_root_.scala.Range")
      .replace("_root_.scala.math.BigDecimal", "_root_.scala.BigDecimal")
      .replace("_root_.scala.math.BigInt", "_root_.scala.BigInt")
      .replace("_root_.scala.math.Equiv", "_root_.scala.Equiv")
      .replace("_root_.scala.math.Fractional", "_root_.scala.Fractional")
      .replace("_root_.scala.math.Integral", "_root_.scala.Integral")
      .replace("_root_.scala.math.Numeric", "_root_.scala.Numeric")
      .replace("_root_.scala.math.Ordered", "_root_.scala.Ordered")
      .replace("_root_.scala.math.Ordering", "_root_.scala.Ordering")
      .replace("_root_.scala.math.PartialOrdering", "_root_.scala.PartialOrdering")
      .replace("_root_.scala.math.PartiallyOrdered", "_root_.scala.PartiallyOrdered")
      .replace("_root_.scala.util.Either", "_root_.scala.Either")
      .replace("_root_.scala.util.Left", "_root_.scala.Left")
      .replace("_root_.scala.util.Right", "_root_.scala.Right")
}
