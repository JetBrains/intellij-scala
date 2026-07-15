package org.jetbrains.plugins.scala.semantic

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.corpus.scala3.{AkkaTest, CatsTest, ScalatestTest, ZioTest}
import org.jetbrains.plugins.scala.corpus.{ProjectCorpusTestBase, ProjectCorpusTestDef}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.getInstance as ScalaApplicationSettings
import org.junit.{Assert, Test}

import java.nio.file.{Files, Path}
import java.util.Comparator

abstract class SemanticTestBase(config: ProjectCorpusTestDef) extends ProjectCorpusTestBase(config) {
//  @Test // Uncomment to print data to files
  def compare(): Unit = {
    val classes = allClasses(excludePackages = Set.empty)

    val directory = Path.of("scala", "semantic-tests", "target", "comparison")
    if (Files.exists(directory)) Files.walk(directory).sorted(Comparator.reverseOrder).forEach(Files.delete(_))
    Files.createDirectory(directory)

    classes.take(50).foreach { cls =>
      println(cls.qualifiedName)
      val (decompiledText, psiText) = textOf(cls.qualifiedName)
      val sourceText = {
        val sourceClass = cls.getSourceMirrorClass.asInstanceOf[ScTypeDefinition]
        sourceClass.getText + sourceClass.baseCompanionTypeDefinition.map("\n\n" + _.getText).getOrElse("")
      }
      Files.write(directory.resolve(cls.name + ".scala"), sourceText.getBytes)
      Files.write(directory.resolve(cls.name + "1.scala"), decompiledText.getBytes)
      Files.write(directory.resolve(cls.name + "2.scala"), psiText.getBytes)
    }
  }

  protected def doTest(fqn: String): Unit = {
    return // Comment to enable the tests cases
    val (decompiledText, sourceText) = textOf(fqn)
    Assert.assertEquals(decompiledText, sourceText)
  }

  private def textOf(fqn: String): (String, String) = {
    val cls = ScalaPsiManager.instance(getProject).getCachedClass(GlobalSearchScope.allScope(getProject), fqn)
      .getOrElse(throw new IllegalArgumentException(fqn))
      .asInstanceOf[ScTypeDefinition]

    val classpath =
      ModuleRootManager.getInstance(getMyFixture.getModule)
        .orderEntries.productionOnly.librariesOnly.classes.getRoots.toSeq
        .map(virtualFile => VfsUtil.getLocalFile(virtualFile).getPath)

    val sourceCls = cls.getSourceMirrorClass.asInstanceOf[ScTypeDefinition]
    Assert.assertTrue(s"Must have a source: ${cls.qualifiedName}", sourceCls != cls)
    Assert.assertFalse(s"Must be in a source file: ${cls.qualifiedName}", sourceCls.isInCompiledFile)

    sourceCls.getText // Necessary to load right-hand sides

    val psiText = try {
      ScalaApplicationSettings.PRECISE_TEXT = true
      ScalaApplicationSettings.PRECISE_TEXT_FOR_TYPE_PARAMETERS = true
      textOfCompilationUnit(sourceCls, withPrivate = true, normalize = true)
    } finally {
      ScalaApplicationSettings.PRECISE_TEXT = false
      ScalaApplicationSettings.PRECISE_TEXT_FOR_TYPE_PARAMETERS = false
    }

    val tastyFile = cls.getContainingFile.getVirtualFile
    Assert.assertTrue(tastyFile.getName, tastyFile.getExtension == "tasty")

    val decompiler = new Decompiler(classpath)
    val decompiledText = decompiler.decompile(tastyFile.getName, tastyFile.contentsToByteArray())

    (decompiledText, psiText)
  }

  // Copy of org.jetbrains.plugins.scala.text.TextToTextTestBase.textOfCompilationUnit
  private def textOfCompilationUnit(cls: ScTypeDefinition, withPrivate: Boolean, normalize: Boolean): String = {
    val packageName = cls.qualifiedName.substring(0, cls.qualifiedName.lastIndexOf('.'))

    val companionTypeAlias = ScalaPsiManager.instance(cls.getProject).getTopLevelDefinitionsByPackage(packageName, cls.getResolveScope).collect {
      case a: ScTypeAlias if a.name == cls.name => a
    }

    val sb = new StringBuilder()

    sb ++= "package " + packageName + "\n"

    val printer = new ClassPrinter(version.isScala3, withPrivate = withPrivate, normalize = normalize)
    ((companionTypeAlias.toSeq :+ cls) ++ cls.baseCompanionTypeDefinition.toSeq).sortBy(_.getTextOffset).foreach {
      case td: ScTypeDefinition => printer.printTo(sb, td)
      case ta: ScTypeAlias => printer.printTo(sb, ta)
    }

    sb.setLength(sb.length - 1)

    sb.toString
  }
}
