package org.jetbrains.plugins.scala.semantic

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.corpus.{ProjectCorpusTestBase, ProjectCorpusTestDef}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.getInstance as ScalaApplicationSettings
import org.junit.Assert

import java.nio.file.{Files, Path}

abstract class SemanticTestBase(config: ProjectCorpusTestDef) extends ProjectCorpusTestBase(config) {
  private val Print =
//    true // Print actual cases and save contents to target/comparison/
    false // Test expected cases

  protected def doTest(classes: String): Unit = {
    val decompiler: Decompiler = {
      val classpath =
        ModuleRootManager.getInstance(getMyFixture.getModule)
          .orderEntries.productionOnly.librariesOnly.classes.getRoots.toSeq
          .map(virtualFile => VfsUtil.getLocalFile(virtualFile).getPath)
      new Decompiler(classpath)
    }

    val classNames =
      if (Print) allClasses(excludePackages = Set.empty).map(_.qualifiedName)
      else classes.split('\n').map(_.trim).filterNot(_.isEmpty).toSeq

    classNames.foreach { name =>
      val isCommented = name.startsWith("//")
      val fqn = if (isCommented) name.substring(2) else name

      val cls = ScalaPsiManager.instance(getProject).getCachedClass(GlobalSearchScope.allScope(getProject), fqn)
        .getOrElse(throw new IllegalArgumentException(fqn)).asInstanceOf[ScTypeDefinition]

      try {
        val (decompiledText, psiText) = textOf(cls, decompiler)

        if (Print) {
          val comment = decompiledText != psiText
          println((if (comment) "//" else "") + fqn)
          val sourceText = {
            val sourceClass = cls.getSourceMirrorClass.asInstanceOf[ScTypeDefinition]
            sourceClass.getText + sourceClass.baseCompanionTypeDefinition.map("\n\n" + _.getText).getOrElse("")
          }
          val directory = Path.of("scala", Seq("semantic-tests", "target", "comparison") ++ fqn.split('.').dropRight(1): _*)
          directory.toFile.mkdirs()
          Files.write(directory.resolve(cls.name + ".scala"), sourceText.getBytes)
          Files.write(directory.resolve(cls.name + "1.scala"), decompiledText.getBytes)
          val file2 = directory.resolve(cls.name + "2.scala").toFile
          if (psiText != decompiledText) {
            Files.write(file2.toPath, psiText.getBytes)
          } else if (file2.exists()) {
            file2.delete()
          }
        } else {
          println(fqn)
          if (isCommented) {
            Assert.assertNotEquals(fqn, decompiledText, psiText)
          } else {
            Assert.assertEquals(fqn, decompiledText, psiText)
          }
        }
      } catch {
        case e: Throwable if Print => System.err.println(fqn + ": " + e.getMessage)
      }
    }
  }

  private def textOf(cls: ScTypeDefinition, decompiler: Decompiler): (String, String) = {
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
