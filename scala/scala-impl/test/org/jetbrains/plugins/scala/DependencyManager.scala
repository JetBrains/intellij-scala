package org.jetbrains.plugins.scala

import java.io.File
import java.nio.file.{Files, Paths}

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import org.apache.ivy.Ivy
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.URLResolver
import org.jetbrains.plugins.scala.DependencyManager.Dependency
import org.jetbrains.plugins.scala.debugger.ScalaVersion

/**
  * SBT-like dependency manager for libraries to be used in tests.
  *
  * To use, override [[org.jetbrains.plugins.scala.debugger.ScalaSdkOwner#loadIvyDependencies()]],<br/>
  * create a new manager and call<br/> [[org.jetbrains.plugins.scala.DependencyManager#loadAll]] or [[org.jetbrains.plugins.scala.DependencyManager#load]]<br/>
  * {{{
  * override protected def loadIvyDependencies(): Unit =
  *     DependencyManager("com.chuusai" %% "shapeless" % "2.3.2").loadAll
  * }}}
  *
  * One can also do this outside loadIvyDependencies, but make sure that all loading is done before [[com.intellij.testFramework.LightPlatformTestCase#setUp()]]
  * is finished to avoid getting "Virtual pointer hasn't been disposed: " errors on tearDown()
  */
class DependencyManager(val deps: Dependency*) {
  import DependencyManager._

  private val homePrefix = sys.props.get("tc.idea.prefix").orElse(sys.props.get("user.home")).map(new File(_)).get
  private val ivyHome = sys.props.get("sbt.ivy.home").map(new File(_)).orElse(Option(new File(homePrefix, ".ivy2"))).get

  private def mkIvyXml(dep: Dependency): String = {
    s"""
      |<ivy-module version="2.0">
      |<info organisation="org.jetbrains.plugins.scala" module="ij-scala-tests"/>
      |  <dependencies>
      |    <dependency org="${dep.org}" name="${dep.artId}" rev="${dep.version}">
      |      <artifact name="${dep.artId}" type="jar"/>
      |    </dependency>
      |  </dependencies>
      |</ivy-module>
    """.stripMargin
  }

  def resolveIvy(d: Dependency): Option[ResolvedDependency] = {
    //creates clear ivy settings
    val ivySettings: IvySettings = new IvySettings
    //url resolver for configuration of maven repo
    val resolver: URLResolver = new URLResolver
    resolver.setM2compatible(true)
    resolver.setName("central")
    //you can specify the url resolution pattern strategy
    resolver.addArtifactPattern("http://repo1.maven.org/maven2/[organisation]/[module]/[revision]/[artifact](-[revision]).[ext]")
    //adding maven repo resolver
    ivySettings.addResolver(resolver)
    //set to the default resolver
    ivySettings.setDefaultResolver(resolver.getName)
    //creates an Ivy instance with settings
    ivySettings.setDefaultIvyUserDir(ivyHome)
    val ivy: Ivy = Ivy.newInstance(ivySettings)

    val ivyfile = File.createTempFile("ivy", ".xml")
    ivyfile.deleteOnExit()
    Files.write(Paths.get(ivyfile.toURI), mkIvyXml(d).getBytes)
    val resolveOptions = new ResolveOptions().setConfs(Array("default"))
    val report = ivy.resolve(ivyfile.toURI.toURL, resolveOptions)
    ivyfile.delete()
    if (!report.getAllProblemMessages.isEmpty || report.getAllArtifactsReports.length == 0)
      None
    else
      Some(ResolvedDependency(d, report.getAllArtifactsReports.apply(0).getLocalFile))
  }


  private def resolveFast(dep: Dependency): Option[ResolvedDependency] = {
    val file = new File(ivyHome, s"cache/${dep.org}/${dep.artId}/jars/${dep.artId}-${dep.version}.jar")
    if (file.exists())
      Some(ResolvedDependency(dep, file))
    else
      None
  }

  private def resolve(dependency: Dependency): Option[ResolvedDependency] = {
    resolveFast(dependency).orElse(resolveIvy(dependency))
  }

  def load(deps: Dependency*)(implicit module: Module): Unit = {
    deps.foreach { d =>
      resolve(d) match {
        case Some(ResolvedDependency(_, file)) =>
          VfsRootAccess.allowRootAccess(file.getCanonicalPath)
          PsiTestUtil.addLibrary(module, file.getName, file.getParent, file.getName)
        case None => println(s"failed ro resolve dependency: $d")
      }
    }
  }
  def loadAll(implicit module: Module): Unit = load(deps:_*)(module)
}

object DependencyManager {

  def apply(deps: Dependency*): DependencyManager = new DependencyManager(deps:_*)

  case class Dependency(org: String, artId: String, version: String) {
    def %(version: String): Dependency = copy(version = version)
  }

  case class ResolvedDependency(info: Dependency, file: File)

  implicit class RichStr(value: String) {
    def %(right: String) = Dependency(value, right, "UNKNOWN")
    def %%(right: String)(implicit scalaVersion: ScalaVersion): Dependency =
      Dependency(value, s"${right}_${scalaVersion.major}", "UNKNOWN")
  }

}

