package org.jetbrains.plugins.scala.text

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiPackage
import com.intellij.testFramework.{PsiTestUtil, TestLoggerKt}
import com.intellij.util.AstLoadingFilter
import org.jetbrains.plugins.scala.DependencyManagerBase.DependencyDescription
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, ScalaReflectLibraryLoader, SmartJDKLoader}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PathExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings.AliasExportSemantics
import org.jetbrains.plugins.scala.text.TextToTextTestBase._
import org.junit.Assert

import java.nio.file.Path
import java.util.Collections
import scala.jdk.CollectionConverters.{ListHasAsScala, SeqHasAsJava}

// SCL-21078
abstract class TextToTextTestBase(dependencies: Seq[DependencyDescription],
                                  packages: Seq[String], packageExceptions: Set[String], minClassCount: Int,
                                  classExceptions: Set[String],
                                  withSources: Boolean = false,
                                  sourceExceptions: Set[String] = Set.empty,
                                  includeScalaReflect: Boolean = false,
                                  includeScalaCompiler: Boolean = false,
                                  astLoadingFilter: Boolean = true)(implicit scalaVersion: ScalaVersion) extends ScalaFixtureTestCase {

  override protected val includeCompilerAsLibrary = includeScalaCompiler

  override protected def supportedIn(version: ScalaVersion) = version >= scalaVersion

  override protected lazy val jdk: Sdk = SmartJDKLoader.createJdk(LanguageLevel.JDK_17)

  override def librariesLoaders =
    super.librariesLoaders :++
      (if (includeScalaReflect) Seq(ScalaReflectLibraryLoader) else Seq.empty)

  override protected def setUpLibraries(implicit module: Module): Unit = {
    super.setUpLibraries(module)

    val classes = IvyManagedLoader(dependencies.map(_.transitive()): _*).resolve(scalaVersion)
    val sources = IvyManagedLoader(classes.filter(it => !ArtifactsWithoutSources(it.info.org, it.info.artId)).map(_.info.sources()): _*).resolve(scalaVersion)
    classes.foreach { cls =>
      val source = sources.find(_.info == cls.info.sources())
      val classRoots = Collections.singletonList(findJarFile(cls.file))
      val sourceRoots = source.map(it => findJarFile(it.file)).toSeq.asJava
      PsiTestUtil.addProjectLibrary(module, cls.info.toString, classRoots, sourceRoots)
    }
  }

  private def findJarFile(file: Path): VirtualFile =
    JarFileSystem.getInstance.refreshAndFindFileByPath(file.toCanonicalPath.toString + "!/")

  def testTextToText(): Unit = {
    val scalaProjectSettings = ScalaProjectSettings.getInstance(getProject)

    scalaProjectSettings.setAliasSemantics(AliasExportSemantics.Definition)
    ScalaApplicationSettings.PRECISE_TEXT = true
    try {
      doTestTextToText()
    } finally {
      scalaProjectSettings.setAliasSemantics(AliasExportSemantics.Export)
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

    val classes = packages
      .map(name => manager.getCachedPackage(name).getOrElse(throw new AssertionError(name)))
      .flatMap(pkg => classesIn(pkg, packageExceptions))
      .filter(cls => if (scalaVersion.isScala3) cls.isInScala3File else !cls.isInScala3File)

    val total = classes.length

    Assert.assertTrue(s"The number of classes: $total < $minClassCount", total >= minClassCount)

    Assert.assertEquals("Class not found", Set.empty, classExceptions -- classes.map(_.qualifiedName).toSet)

    println(s"Testing $total classes:")

    classes.zipWithIndex.foreach { case (cls, i) =>
      println(f"${i + 1}%04d/$total%s: ${cls.qualifiedName}")

      Assert.assertTrue("Must be in a compiled file: ${cls.qualifiedName}", cls.isInCompiledFile)

      val actual = {
        val text = withAstLoadingFilter(textOfCompilationUnit(cls, withPrivate = true, normalize = false))
        val errors = TestLoggerKt.getErrorLog.takeLoggedErrors()
        if (errors.isEmpty) text else errors.asScala.map(_.toString).mkString("\n")
      }

      val expected = {
        val s1 = cls.getContainingFile.getText
        // TODO Function type by-name parameters, SCL-21149
        val s2 = if (cls.qualifiedName.startsWith("scalaz.")) s1.replace("(=> ", "(").replace(", => ", ", ").replaceAll("\\((\\S+)\\) => ", "$1 => ") else s1
        s2.replaceAll("\\.super\\[.*?\\*/\\]\\.", ".this.")
      }

      if (classExceptions(cls.qualifiedName)) {
        Assert.assertNotEquals(expected, actual, s"Expected to contain errors: ${cls.qualifiedName}")
      } else {
        Assert.assertEquals(s"${cls.qualifiedName} [decompiled | outlines]", expected, actual)

        if (withSources && !ClassesWithoutSource(cls.name)) {
          val sourceCls = cls.getSourceMirrorClass.asInstanceOf[ScTypeDefinition]
          Assert.assertTrue(s"Must have a source: ${cls.qualifiedName}", sourceCls != cls)
          Assert.assertFalse(s"Must be in a source file: ${cls.qualifiedName}", sourceCls.isInCompiledFile)

          val actualSource = textOfCompilationUnit(sourceCls, withPrivate = false, normalize = true)

          if (sourceExceptions(cls.qualifiedName)) {
            Assert.assertNotEquals(s"Expected to contain errors: ${cls.qualifiedName}", expected, actualSource)
          } else {
            Assert.assertEquals(s"${cls.qualifiedName} [decompiled | source outlines]", expected, actualSource)
          }
        }
      }
    }

    println("Done.")
  }

  private def classesIn(pkg: PsiPackage, exceptions: Set[String]): Seq[ScTypeDefinition] = {
    val packageClasses = pkg.getClasses
      .collect({ case c: ScTypeDefinition if c.isInCompiledFile && !(c.is[ScObject] && c.baseCompanion.isDefined) => c })
      .sortBy(_.qualifiedName)

    val subpackageClasses = pkg.getSubPackages
      .filter(pkg => !exceptions(pkg.getQualifiedName))
      .sortBy(_.getQualifiedName)
      .flatMap(classesIn(_, exceptions))

    packageClasses.toSeq ++ subpackageClasses.toSeq
  }

  private def textOfCompilationUnit(cls: ScTypeDefinition, withPrivate: Boolean, normalize: Boolean): String = {
    val packageName = cls.qualifiedName.substring(0, cls.qualifiedName.lastIndexOf('.'))

    val companionTypeAlias = ScalaPsiManager.instance(cls.getProject).getTopLevelDefinitionsByPackage(packageName, cls.getResolveScope).collect {
      case a: ScTypeAlias if a.name == cls.name => a
    }

    val sb = new StringBuilder()

    sb ++= "package " + packageName + "\n"

    val printer = new ClassPrinter(scalaVersion.isScala3, withPrivate = withPrivate, normalize = normalize)
    companionTypeAlias.foreach(printer.printTo(sb, _))
    printer.printTo(sb, cls)
    cls.baseCompanion.foreach(printer.printTo(sb, _))

    sb.setLength(sb.length - 1)

    sb.toString
  }
}

private object TextToTextTestBase {
  private val ArtifactsWithoutSources = Set(("com.google.guava", "listenablefuture"))

  private val ClassesWithoutSource = Set("BuildInfo")
}
