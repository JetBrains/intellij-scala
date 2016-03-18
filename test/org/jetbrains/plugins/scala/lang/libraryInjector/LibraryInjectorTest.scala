package org.jetbrains.plugins.scala.lang.libraryInjector

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.util.zip.{ZipEntry, ZipOutputStream}

import com.intellij.compiler.CompilerTestUtil
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.testFramework.{ModuleTestCase, PsiTestUtil}
import org.jetbrains.plugins.scala.base.ScalaLibraryLoader
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.components.libinjection.LibraryInjectorLoader
import org.jetbrains.plugins.scala.debugger.{DebuggerTestUtil, ScalaVersion}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.util.ScalaUtil
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * Created by mucianm on 16.03.16.
  */
class LibraryInjectorTest extends ModuleTestCase with ScalaVersion {

  val LIBRARY_NAME = "dummy_lib.jar"
  private var scalaLibraryLoader: ScalaLibraryLoader = null

  trait Zipable {
    def zip(toDir: File): File = ???
    def withParent(name: String): Zipable
  }
  case class ZFile(name: String, data: String) extends Zipable {
    override def withParent(parentName: String) = copy(name = s"$parentName/$name")
  }
  case class ZDir(name: String, files: Seq[Zipable]) extends Zipable{
    override def zip(toDir: File): File = {
      val file = new File(toDir, LIBRARY_NAME)
      val zfs = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)))
      def doZip(zipable: Zipable): Unit = {
        zipable match {
          case ZDir(zname, zfiles) =>
            zfs.putNextEntry(new ZipEntry(zname+"/"))
            zfiles.foreach(z=>doZip(z.withParent(zname)))
            zfs.closeEntry()
          case ZFile(zname, zdata) =>
            zfs.putNextEntry(new ZipEntry(zname))
            zfs.write(zdata.getBytes("UTF-8"), 0, zdata.length)
            zfs.closeEntry()
        }
      }
      doZip(this)
      zfs.close()
      file
    }

    override def withParent(parentName: String) = copy(name = s"$parentName/$name")
  }

  override def setUp(): Unit = {
    super.setUp()

    VfsRootAccess.allowRootAccess()
    CompilerTestUtil.enableExternalCompiler()
    DebuggerTestUtil.setCompileServerSettings()
    DebuggerTestUtil.forceJdk8ForBuildProcess()
  }

  override def setUpModule(): Unit = {
    super.setUpModule()
    scalaLibraryLoader = new ScalaLibraryLoader(getProject, getModule, myProject.getBasePath,
      isIncludeReflectLibrary = true, javaSdk = Some(getTestProjectJdk))

    scalaLibraryLoader.loadScala(scalaSdkVersion)
    addLibrary(testData(getTestName(false)).zip(ScalaUtil.createTmpDir("injectorTestLib", "")))
  }

  protected override def tearDown() {
    CompilerTestUtil.disableExternalCompiler(myProject)
    CompileServerLauncher.instance.stop()
    scalaLibraryLoader.clean()
    super.tearDown()
  }

  protected def addLibrary(library: File) {
    VfsRootAccess.allowRootAccess(library.getAbsolutePath)
    PsiTestUtil.addLibrary(myModule, library.getAbsolutePath)
    VirtualFilePointerManager.getInstance().asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
  }

  override protected def getTestProjectJdk: Sdk = {
    DebuggerTestUtil.findJdk8()
  }

  val simpleInjector = {
    val manifest =
      """
        |<intellij-compat>
        |    <scala-plugin since-version="0.0.0" until-version="9.9.9">
        |        <psi-injector interface="org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector"
        |         implementation="com.foo.bar.Implementation">
        |            <source>META-INF/Implementation.scala</source>
        |            <source>META-INF/Foo.scala</source>
        |        </psi-injector>
        |    </scala-plugin>
        |</intellij-compat>
        |
      """.stripMargin

    val implementationClass =
      """
        |package com.foo.bar
        |import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
        |
        |class Implementation extends SyntheticMembersInjector { val foo = new Foo }
      """.stripMargin

    val fooClass =
      """
        |package com.foo.bar
        |class Foo
      """.stripMargin

    ZDir("META-INF",
      Seq(
        ZFile(LibraryInjectorLoader.INJECTOR_MANIFEST_NAME, manifest),
        ZFile("Implementation.scala", implementationClass),
        ZFile("Foo.scala", fooClass)
      )
    )
  }

  val testData = Map("Simple" -> simpleInjector)

  def testSimple() {
    VirtualFilePointerManager.getInstance().asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
    assert(LibraryInjectorLoader.getInstance(myProject).getInjectorClasses(classOf[SyntheticMembersInjector]).nonEmpty)
  }


  override protected def scalaSdkVersion: ScalaSdkVersion = ScalaSdkVersion._2_11
}
