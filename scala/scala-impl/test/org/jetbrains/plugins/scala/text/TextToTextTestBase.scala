package org.jetbrains.plugins.scala.text

import com.intellij.psi.PsiPackage
import com.intellij.util.AstLoadingFilter
import org.jetbrains.plugins.scala.DependencyManagerBase.DependencyDescription
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, ScalaReflectLibraryLoader}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}
import org.junit.Assert

// SCL-21078
abstract class TextToTextTestBase(dependencies: Seq[DependencyDescription],
                                  packages: Seq[String], packageExceptions: Set[String], minClassCount: Int,
                                  classExceptions: Set[String],
                                  includeScalaReflect: Boolean = false,
                                  includeScalaCompiler: Boolean = false,
                                  astLoadingFilter: Boolean = true)(implicit scalaVersion: ScalaVersion) extends ScalaFixtureTestCase {

  override protected val includeCompilerAsLibrary = includeScalaCompiler

  override protected def supportedIn(version: ScalaVersion) = version >= scalaVersion

  override def librariesLoaders =
    super.librariesLoaders :+
      IvyManagedLoader(dependencies.map(_.transitive()): _*) :++
      (if (includeScalaReflect) Seq(ScalaReflectLibraryLoader) else Seq.empty)

  def testTextToText(): Unit = {
    try {
      ScalaApplicationSettings.PRECISE_TEXT = true
      if (astLoadingFilter) {
        AstLoadingFilter.disallowTreeLoading { () =>
          doTestTextToText()
        }
      } else {
        doTestTextToText()
      }
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
      .filter(cls => if (scalaVersion.isScala3) cls.isInScala3File else !cls.isInScala3File)

    val total = classes.length

    Assert.assertTrue(s"The number of classes: $total < $minClassCount", total >= minClassCount)

    println(s"Testing $total classes:")

    classes.zipWithIndex.foreach { case (cls, i) =>
      println(f"$i%04d/$total%s: ${cls.qualifiedName}")

      val actual = try {
        textOfCompilationUnit(cls)
      } catch {
        case e: Throwable => e.toString
      }

      val expected = {
        val s1 = cls.getContainingFile.getText
        // TODO Function type by-name parameters, SCL-21149
        val s2 = if (cls.qualifiedName.startsWith("scalaz.")) s1.replace("(=> ", "(").replace(", => ", ", ").replaceAll("\\((\\S+)\\) => ", "$1 => ") else s1
        s2.replaceAll("\\.super\\[.*?\\*/\\]\\.", ".this.")
      }

      if (!classExceptions(cls.qualifiedName)) {
        Assert.assertEquals(cls.qualifiedName, expected, actual)
      } else {
        Assert.assertFalse(s"Must contain errors: ${cls.qualifiedName}", expected == actual)
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
    val packageName = cls.qualifiedName.substring(0, cls.qualifiedName.lastIndexOf('.'))

    val companionTypeAlias = ScalaPsiManager.instance(cls.getProject).getTopLevelDefinitionsByPackage(packageName, cls.getResolveScope).collect {
      case a: ScTypeAlias if a.name == cls.name => a
    }

    val sb = new StringBuilder()

    sb ++= "package " + packageName + "\n"

    val printer = new ClassPrinter(scalaVersion.isScala3)
    companionTypeAlias.foreach(printer.printTo(sb, _))
    printer.printTo(sb, cls)
    cls.baseCompanion.foreach(printer.printTo(sb, _))

    sb.setLength(sb.length - 1)

    sb.toString
  }
}
