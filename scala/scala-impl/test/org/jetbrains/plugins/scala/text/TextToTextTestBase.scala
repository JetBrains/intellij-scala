package org.jetbrains.plugins.scala.text

import com.intellij.psi.PsiPackage
import org.jetbrains.plugins.scala.DependencyManagerBase.DependencyDescription
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyManagedLoader
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}
import org.junit.Assert

// SCL-21078
abstract class TextToTextTestBase extends ScalaFixtureTestCase {
  protected def dependencies: Seq[DependencyDescription]

  protected def packages: Seq[String]

  protected def packageExceptions: Set[String]

  protected def classExceptions: Set[String]

  protected def minClassCount: Int

  override def librariesLoaders =
    super.librariesLoaders :+ IvyManagedLoader(dependencies: _*)

  def testTextToText(): Unit = {
    try {
      ScalaApplicationSettings.PRECISE_TEXT = true
      doTestTextToText()
    } finally {
      ScalaApplicationSettings.PRECISE_TEXT = false
    }
  }

  private def doTestTextToText(): Unit = {
    val manager = ScalaPsiManager.instance(getProject)

    println("Collecting classes...")

    val classes = packages
      .map(name => manager.getCachedPackage(name).getOrElse(throw new AssertionError(name)))
      .flatMap(pkg => classesIn(pkg, packageExceptions))

    val total = classes.length

    Assert.assertTrue(total.toString, total > minClassCount)

    println(s"Testing $total classes:")

    classes.zipWithIndex.foreach { case (cls, i) =>
      println(f"$i%04d/$total%s: ${cls.qualifiedName}")

      val expected = {
        val s1 = cls.getContainingFile.getText
        // TODO Function type by-name parameters, SCL-21149
        val s2 = if (cls.qualifiedName.startsWith("scalaz.")) s1.replace("(=> ", "(").replace(", => ", ", ").replaceAll("\\((\\S+)\\) => ", "$1 => ") else s1
        s2.replaceAll("\\.super\\[.*?\\*/\\]\\.", ".this.")
      }

      val actual = textOfCompilationUnit(cls)

      if (!classExceptions(cls.qualifiedName)) {
        Assert.assertEquals(cls.qualifiedName, expected, actual)
      } else {
        Assert.assertFalse(cls.qualifiedName, expected == actual)
      }
    }

    println("Done.")
  }

  private def classesIn(pkg: PsiPackage, exceptions: Set[String]): Seq[ScTypeDefinition] = {
    val packageClasses = pkg.getClasses
      .collect({case c: ScTypeDefinition if c.isInCompiledFile && !(c.isInstanceOf[ScObject] && c.baseCompanion.isDefined) => c})
      .sortBy(_.qualifiedName)

    val subpackageClasses = pkg.getSubPackages
      .filter(pkg => !exceptions(pkg.getQualifiedName))
      .sortBy(_.getQualifiedName)
      .flatMap(classesIn(_, exceptions))

    packageClasses.toSeq ++ subpackageClasses.toSeq
  }

  private def textOfCompilationUnit(cls: ScTypeDefinition): String = {
    val sb = new StringBuilder()

    sb ++= "package " + cls.qualifiedName.substring(0, cls.qualifiedName.lastIndexOf('.')) + "\n"

    ClassPrinter.printTo(sb, cls)
    cls.baseCompanion.foreach { obj =>
      ClassPrinter.printTo(sb, obj)
    }

    sb.setLength(sb.length - 1)

    sb.toString
  }
}
