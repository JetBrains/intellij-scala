import sbt.Def.Initialize
import sbt.Keys.{appConfiguration, scalaBinaryVersion, state, _}
import sbt._
import sbt.internal.inc.ScalaInstance
import sbt.io.IO
import sbt.librarymanagement.{DependencyResolution, ScalaModuleInfo, UnresolvedWarningConfiguration, UpdateConfiguration}
import xsbti.AppConfiguration
import xsbti.compile._

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.Optional
import scala.collection.mutable
import scala.util.Properties.isJavaAtLeast

// TODO: this is a workaround to make Scala3 modules compilable in SBT 1.3.x
//  Scala 3.0.0 only works with SBT >= 1.5
//  Remove this once we update to SBT 1.5.x (currently there are some issues with TeamCity integration)
// Copied from https://github.com/lampepfl/dotty/blob/RC1/sbt-dotty/src/dotty/tools/sbtplugin/DottyPlugin.scala
// with some changes to scala3 artifact version naming
// (since Scala 3.0.0 (release) short suffix is used: `
// scala3-compiler_3-3.0.0.jar` instead of full `scala3-compiler_3-3.0.0.jar`
// (note, in scala2, compiler jars didn't have extra suffix at all)
object DottyPlugin_Patched extends AutoPlugin {
  object autoImport {
    val isDotty = settingKey[Boolean]("Is this project compiled with Dotty?")
    val isDottyJS = settingKey[Boolean]("Is this project compiled with Dotty and Scala.js?")

    val useScaladoc = settingKey[Boolean]("Use scaladoc as the documentation tool")
    val useScala3doc = useScaladoc
    val tastyFiles = taskKey[Seq[File]]("List all testy files")

    implicit class DottyCompatModuleID(moduleID: ModuleID) {
      /** If this ModuleID cross-version is a Dotty version, replace it
       *  by the Scala 2.x version that the Dotty version is retro-compatible with,
       *  otherwise do nothing.
       *
       *  This setting is useful when your build contains dependencies that have only
       *  been published with Scala 2.x, if you have:
       *  {{{
       *  libraryDependencies += "a" %% "b" % "c"
       *  }}}
       *  you can replace it by:
       *  {{{
       *  libraryDependencies += ("a" %% "b" % "c").withDottyCompat(scalaVersion.value)
       *  }}}
       *  This will have no effect when compiling with Scala 2.x, but when compiling
       *  with Dotty this will change the cross-version to a Scala 2.x one. This
       *  works because Dotty is currently retro-compatible with Scala 2.x.
       *
       *  NOTE: As a special-case, the cross-version of scala3-library and scala3-compiler
       *  will never be rewritten because we know that they're Scala 3 only.
       *  This makes it possible to do something like:
       *  {{{
       *  libraryDependencies ~= (_.map(_.withDottyCompat(scalaVersion.value)))
       *  }}}
       */
      def withDottyCompat(scalaVersion: String): ModuleID = {
        val name = moduleID.name
        if (name != "scala3-library" && name != "scala3-compiler" &&
            name != "dotty" && name != "dotty-library" && name != "dotty-compiler")
          moduleID.crossVersion match {
            case binary: librarymanagement.Binary =>
              val compatVersion =
                CrossVersion.partialVersion(scalaVersion) match {
                  case Some((3, _)) =>
                    "2.13"
                  case Some((0, minor)) =>
                    if (minor > 18 || scalaVersion.startsWith("0.18.1"))
                      "2.13"
                    else
                      "2.12"
                  case _ =>
                    ""
                }
              if (compatVersion.nonEmpty)
                moduleID.cross(CrossVersion.constant(binary.prefix + compatVersion + binary.suffix))
              else
                moduleID
            case _ =>
              moduleID
          }
        else
          moduleID
      }
    }
  }

  import autoImport._

  override def requires: Plugins = plugins.JvmPlugin
  override def trigger = allRequirements

  /** Patches the IncOptions so that .tasty files are pruned as needed.
   *
   *  This code is adapted from `scalaJSPatchIncOptions` in Scala.js, which needs
   *  to do the exact same thing but for .sjsir files.
   *
   *  This complicated logic patches the ClassfileManager factory of the given
   *  IncOptions with one that is aware of .tasty files emitted by the Dotty
   *  compiler. This makes sure that, when a .class file must be deleted, the
   *  corresponding .tasty file is also deleted.
   *
   *  To support older versions of dotty, this also takes care of .hasTasty
   *  files, although they are not used anymore.
   */
  def dottyPatchIncOptions(incOptions: IncOptions): IncOptions = {
    val tastyFileManager = new TastyFileManager

    // Once sbt/zinc#562 is fixed, can be:
    // val newExternalHooks =
    //   incOptions.externalHooks.withExternalClassFileManager(tastyFileManager)
    val inheritedHooks = incOptions.externalHooks
    val external = Optional.of(tastyFileManager: ClassFileManager)
    val prevManager = inheritedHooks.getExternalClassFileManager
    val fileManager: Optional[ClassFileManager] =
      if (prevManager.isPresent) Optional.of(WrappedClassFileManager.of(prevManager.get, external))
      else external
    val newExternalHooks = new DefaultExternalHooks(inheritedHooks.getExternalLookup, fileManager)

    incOptions.withExternalHooks(newExternalHooks)
  }

  override val globalSettings: Seq[Def.Setting[_]] = Seq(
    onLoad in Global := onLoad.in(Global).value.andThen { state =>

      val requiredVersion = ">=1.4.4"

      val sbtV = sbtVersion.value
//      if (!VersionNumber(sbtV).matchesSemVer(SemanticSelector(requiredVersion)))
//        sys.error(s"The sbt-dotty plugin cannot work with this version of sbt ($sbtV), sbt $requiredVersion is required.")

      state
    }
  )

  // https://github.com/sbt/sbt/issues/3110
  val Def = sbt.Def

  private def scala3Artefact(version: String, name: String) =
    if (version.startsWith("0.")) s"dotty-$name"
    else if (version.startsWith("3.")) s"scala3-$name"
    else throw new RuntimeException(
      s"Cannot construct a Scala 3 artefact name $name for a non-Scala3 " +
      s"scala version ${version}")

  override def projectSettings: Seq[Setting[_]] = {
    Seq(
      isDotty := scalaVersion.value.startsWith("0.") || scalaVersion.value.startsWith("3."),

      /* The way the integration with Scala.js works basically assumes that the settings of ScalaJSPlugin
       * will be applied before those of DottyPlugin. It seems to be the case in the tests I did, perhaps
       * because ScalaJSPlugin is explicitly enabled, while DottyPlugin is triggered. However, I could
       * not find an authoritative source on the topic.
       *
       * There is an alternative implementation that would not have that assumption: it would be to have
       * another DottyJSPlugin, that would be auto-triggered by the presence of *both* DottyPlugin and
       * ScalaJSPlugin. That plugin would be guaranteed to have its settings be applied after both of them,
       * by the documented rules. However, that would require sbt-dotty to depend on sbt-scalajs to be
       * able to refer to ScalaJSPlugin.
       *
       * When the logic of sbt-dotty moves to sbt itself, the logic specific to the Dotty-Scala.js
       * combination will have to move to sbt-scalajs. Doing so currently wouldn't work since we
       * observe that the settings of DottyPlugin are applied after ScalaJSPlugin, so ScalaJSPlugin
       * wouldn't be able to fix up things like the dependency on dotty-library.
       */
      isDottyJS := {
        isDotty.value && (crossVersion.value match {
          case binary: librarymanagement.Binary => binary.prefix.contains("sjs1_")
          case _                                => false
        })
      },

      scalaOrganization := {
        if (scalaVersion.value.startsWith("3."))
          "org.scala-lang"
        else
          scalaOrganization.value
      },

      incOptions in Compile := {
        val inc = (incOptions in Compile).value
        if (isDotty.value)
          dottyPatchIncOptions(inc)
        else
          inc
      },


      scalaCompilerBridgeBinaryJar := Def.settingDyn {
        if (isDotty.value) Def.task {
          val updateReport = fetchArtifactsOf(
            scalaOrganization.value % scala3Artefact(scalaVersion.value, "sbt-bridge") % scalaVersion.value,
            dependencyResolution.value,
            scalaModuleInfo.value,
            updateConfiguration.value,
            (unresolvedWarningConfiguration in update).value,
            streams.value.log
          )
          Option(getJar(updateReport, scalaOrganization.value, scala3Artefact(scalaVersion.value, "sbt-bridge"), scalaVersion.value))
        }
        else Def.task {
          None: Option[File]
        }
      }.value,

      // Prevent the consoleProject task from using the Scala 3 compiler bridge
      // The consoleProject must load the Scala 2.12 instance and the sbt classpath
      consoleProject / scalaCompilerBridgeBinaryJar := None,

      // Needed for RCs publishing
      scalaBinaryVersion := {
        scalaVersion.value.split("[\\.-]").toList match {
          case "0" :: minor :: _ => s"0.$minor"
          case "3" :: minor :: patch :: suffix =>
            s"3.$minor.$patch" + (suffix match {
              case milestone :: _ => s"-$milestone"
              case Nil => ""
            })
          case _ => scalaBinaryVersion.value
        }
      },

      // We want:
      //
      // 1. Nothing but the Java standard library on the _JVM_ bootclasspath
      //    (starting with Java 9 we cannot inspect it so we don't have a choice)
      //
      // 2. scala-library, dotty-library, dotty-compiler and its dependencies on the _JVM_
      //    classpath, because we need all of those to actually run the compiler.
      //    NOTE: All of those should have the *same version* (equal to scalaVersion
      //    for everything but scala-library).
      //    (Complication: because dottydoc is a separate artifact with its own dependencies,
      //     running it requires putting extra dependencies on the _JVM_ classpath)
      //
      // 3. scala-library, dotty-library on the _compiler_ bootclasspath or
      //    classpath (the only difference between them is that the compiler
      //    bootclasspath has higher priority, but that should never
      //    make a difference in a sane environment).
      //    NOTE: the versions of {scala,dotty}-library used here do not necessarily
      //    match the one used in 2. because a dependency of the current project might
      //    require a more recent standard library version, this is OK
      //    TODO: ... but if macros are used we might be forced to use the same
      //    versions in the JVM and compiler classpaths to avoid problems, this
      //    needs to be investigated.
      //
      // 4. every other dependency of the user project on the _compiler_
      //    classpath.
      //
      // By default, zinc will put on the compiler bootclasspath the
      // scala-library used on the JVM classpath, even if the current project
      // transitively depends on a newer scala-library (this works because Scala
      // 2 guarantees forward- and backward- binary compatibility, but we don't
      // necessarily want to keep doing that in Scala 3).
      // So for the moment, let's just put nothing at all on the compiler
      // bootclasspath, and instead let sbt dependency management choose which
      // scala-library and dotty-library to put on the compiler classpath.
      // Maybe eventually we should just remove the compiler bootclasspath since
      // it's a source of complication with only dubious benefits.

      // sbt crazy scoping rules mean that when we override `classpathOptions`
      // below we also override `classpathOptions in console` which is normally
      // set in https://github.com/sbt/sbt/blob/b6f02b9b8cd0abb15e3d8856fd76b570deb1bd61/main/src/main/scala/sbt/Defaults.scala#L503,
      // this breaks `sbt console` in Scala 2 projects.
      // There seems to be no way to avoid stomping over task-scoped settings,
      // so we need to manually set `classpathOptions in console` to something sensible,
      // ideally this would be "whatever would be set if this plugin was not enabled",
      // but I can't find a way to do this, so we default to whatever is set in ThisBuild.
      classpathOptions in console := {
        if (isDotty.value)
          classpathOptions.value // The Dotty REPL doesn't require anything special on its classpath
        else
          (classpathOptions in console in ThisBuild).value
      },
      classpathOptions := {
        val old = classpathOptions.value
        if (isDotty.value)
          old
            .withAutoBoot(false)      // we don't put the library on the compiler bootclasspath (as explained above)
            .withFilterLibrary(false) // ...instead, we put it on the compiler classpath
        else
          old
      },
      // ... but when running under Java 8, we still need a compiler bootclasspath
      // that contains the JVM bootclasspath, otherwise sbt incremental
      // compilation breaks.
      scalacOptions ++= {
        if (isDotty.value && !isJavaAtLeast("9"))
          Seq("-bootclasspath", sys.props("sun.boot.class.path"))
        else
          Seq()
      },
      // If the current scalaVersion is N and we transitively depend on
      // {scala, dotty}-{library, compiler, ...} M where M > N, we want the
      // newest version on our compiler classpath, but sbt by default will
      // instead rewrite all our dependencies to version N, the following line
      // prevents this behavior.
      scalaModuleInfo := {
        val old = scalaModuleInfo.value
        if (isDotty.value)
          old.map(_.withOverrideScalaVersion(false))
        else
          old
      },
      // Prevent sbt from creating a ScalaTool configuration
      managedScalaInstance := {
        val old = managedScalaInstance.value
        if (isDotty.value)
          false
        else
          old
      },
      // ... instead, we'll fetch the compiler and its dependencies ourselves.
      scalaInstance := Def.taskDyn {
        if (isDotty.value)
          dottyScalaInstanceTask(scala3Artefact(scalaVersion.value, "compiler"))
        else
          Def.valueStrict { scalaInstance.taskValue }
      }.value,

      // Configuration for the doctool
      resolvers ++= (if(!useScaladoc.value) Nil else Seq(Resolver.jcenterRepo)),
      useScaladoc := {
        val v = scalaVersion.value
        v.startsWith("3.0.0") && !v.startsWith("3.0.0-M1") && !v.startsWith("3.0.0-M2")
      },
      // We need to add doctool classes to the classpath so they can be called
      scalaInstance in doc := Def.taskDyn {
        if (isDotty.value)
          if (useScaladoc.value) {
            val v = scalaVersion.value
            val shouldUseScala3doc =
              v.startsWith("3.0.0-M1") || v.startsWith("3.0.0-M2") || v.startsWith("3.0.0-M3")  || v.startsWith("3.0.0-RC1-bin-20210")
            val name = if (shouldUseScala3doc) "scala3doc" else "scaladoc"
            dottyScalaInstanceTask(name)
          } else dottyScalaInstanceTask(scala3Artefact(scalaVersion.value, "doc"))
        else
          Def.valueStrict { (scalaInstance in doc).taskValue }
      }.value,

      // Because managedScalaInstance is false, sbt won't add the standard library to our dependencies for us
      libraryDependencies ++= {
        if (isDotty.value && autoScalaLibrary.value) {
          val scalaVersionValue = scalaVersion.value
          val name =
            if (isDottyJS.value) scala3Artefact(scalaVersionValue, "library_sjs1")
            else scala3Artefact(scalaVersionValue, "library")
          if (scalaVersionValue == "3.0.0")
            Seq(scalaOrganization.value % s"${name}_${scalaVersionValue.head}" % scalaVersionValue)
          else
            Seq(scalaOrganization.value %% name % scalaVersionValue)
        } else
          Seq()
      },

      // Patch up some more options if this is Dotty with Scala.js
      scalacOptions := {
        val prev = scalacOptions.value
        /* The `&& !prev.contains("-scalajs")` is future-proof, for when sbt-scalajs adds that
         * option itself but sbt-dotty is still required for the other Dotty-related stuff.
         */
        if (isDottyJS.value && !prev.contains("-scalajs")) prev :+ "-scalajs"
        else prev
      },
      libraryDependencies := {
        val prev = libraryDependencies.value
        if (!isDottyJS.value) {
          prev
        } else {
          prev
            /* Remove the dependencies we don't want:
             * * We don't want scalajs-library, because we need the one that comes
             *   as a dependency of dotty-library_sjs1
             * * We don't want scalajs-compiler, because that's a compiler plugin,
             *   which is replaced by the `-scalajs` flag in dotc.
             */
            .filterNot { moduleID =>
              moduleID.organization == "org.scala-js" && (
                moduleID.name == "scalajs-library" || moduleID.name == "scalajs-compiler"
              )
            }
            // Apply withDottyCompat to the dependency on scalajs-test-bridge
            .map { moduleID =>
              if (moduleID.organization == "org.scala-js" && moduleID.name == "scalajs-test-bridge")
                moduleID.withDottyCompat(scalaVersion.value)
              else
                moduleID
            }
        }
      },

      // Turns off the warning:
      // [warn] Binary version (0.9.0-RC1) for dependency ...;0.9.0-RC1
      // [warn]  in ... differs from Scala binary version in project (0.9).
      scalaModuleInfo := {
        val old = scalaModuleInfo.value
        if (isDotty.value)
          old.map(_.withCheckExplicit(false))
        else
          old
      }
    ) ++ inConfig(Compile)(docSettings) ++ inConfig(Test)(docSettings)
  }

  private val docSettings = inTask(doc)(Seq(
    tastyFiles := {
      val sources = compile.value // Ensure that everything is compiled, so TASTy is available.
      // sbt is too smart and do not start doc task if there are no *.scala files defined
      file("___fake___.scala") +:
        (classDirectory.value ** "*.tasty").get.map(_.getAbsoluteFile)
    },
    sources := Def.taskDyn[Seq[File]] {
      val originalSources = sources.value
      if (isDotty.value && useScaladoc.value && originalSources.nonEmpty)
        Def.task { tastyFiles.value }
      else Def.task { originalSources }
    }.value,
    scalacOptions ++= {
      if (isDotty.value) {
        val projectName =
          if (configuration.value == Compile)
            name.value
          else
            s"${name.value}-${configuration.value}"
        Seq(
          "-project", projectName,
          "-from-tasty"
        )
      }
      else
        Seq()
    }
  ))

  /** Fetch artifacts for moduleID */
  def fetchArtifactsOf(
    moduleID: ModuleID,
    dependencyRes: DependencyResolution,
    scalaInfo: Option[ScalaModuleInfo],
    updateConfig: UpdateConfiguration,
    warningConfig: UnresolvedWarningConfiguration,
    log: Logger): UpdateReport = {
    val descriptor = dependencyRes.wrapDependencyInModule(moduleID, scalaInfo)

    dependencyRes.update(descriptor, updateConfig, warningConfig, log) match {
      case Right(report) =>
        report
      case Left(warning) =>
        throw new MessageOnlyException(
          s"Couldn't retrieve `$moduleID` : ${warning.resolveException.getMessage}.")
    }
  }

  /** Get all jars in updateReport that match the given filter. */
  def getJars(updateReport: UpdateReport, organization: NameFilter, name: NameFilter, revision: NameFilter): Seq[File] = {
    updateReport.select(
      configurationFilter(Runtime.name),
      moduleFilter(organization, name, revision),
      artifactFilter(extension = "jar", classifier = "")
    )
  }

  /** Get the single jar in updateReport that match the given filter.
   *  If zero or more than one jar match, an exception will be thrown.
   */
  def getJar(updateReport: UpdateReport, organization: NameFilter, name: NameFilter, revision: NameFilter): File = {
    val jars = getJars(updateReport, organization, name, revision)
    assert(jars.size == 1, s"There should only be one $name jar but found: $jars")
    jars.head
  }

  /** Create a scalaInstance task that uses Dotty based on `moduleName`. */
  def dottyScalaInstanceTask(moduleName: String): Initialize[Task[ScalaInstance]] = Def.task {
    val scalaBinaryVersionValue = scalaBinaryVersion.value
    val scalaBinaryVersionSuffix =
      if (scalaBinaryVersionValue == "3.0.0") scalaBinaryVersionValue.head
      else scalaBinaryVersionValue

    val updateReport =
      fetchArtifactsOf(
        scalaOrganization.value % (s"${moduleName}_$scalaBinaryVersionSuffix") % scalaVersion.value,
        dependencyResolution.value,
        scalaModuleInfo.value,
        updateConfiguration.value,
        (unresolvedWarningConfiguration in update).value,
        streams.value.log)
    val scalaLibraryJar = getJar(updateReport,
      "org.scala-lang", "scala-library", revision = AllPassFilter)

    val dottyLibraryJar = getJar(updateReport,
      scalaOrganization.value, scala3Artefact(scalaVersion.value, s"library_$scalaBinaryVersionSuffix"), scalaVersion.value)
    val compilerJar = getJar(updateReport,
      scalaOrganization.value, scala3Artefact(scalaVersion.value, s"compiler_$scalaBinaryVersionSuffix"), scalaVersion.value)
    val allJars =
      getJars(updateReport, AllPassFilter, AllPassFilter, AllPassFilter)

    makeScalaInstance(
      state.value,
      scalaVersion.value,
      scalaLibraryJar,
      dottyLibraryJar,
      compilerJar,
      allJars,
      appConfiguration.value
    )
  }

  // Adapted from private mkScalaInstance in sbt
  def makeScalaInstance(
    state: State, dottyVersion: String, scalaLibrary: File, dottyLibrary: File, compiler: File, all: Seq[File], appConfiguration: AppConfiguration
  ): ScalaInstance = {
    /**
      * The compiler bridge must load the xsbti classes from the sbt
      * classloader, and similarly the Scala repl must load the sbt provided
      * jline terminal. To do so we add the `appConfiguration` loader in
      * the parent hierarchy of the scala 3 instance loader.
      *
      * The [[TopClassLoader]] ensures that the xsbti and jline classes
      * only are loaded from the sbt loader. That is necessary because
      * the sbt class loader contains the Scala 2.12 library and compiler
      * bridge.
      */
    val topLoader = new TopClassLoader(appConfiguration.provider.loader)

    val libraryJars = Array(dottyLibrary, scalaLibrary)
    val libraryLoader = state.classLoaderCache.cachedCustomClassloader(
      libraryJars.toList,
      () => new URLClassLoader(libraryJars.map(_.toURI.toURL), topLoader)
    )

    class DottyLoader
        extends URLClassLoader(all.map(_.toURI.toURL).toArray, libraryLoader)
    val fullLoader = state.classLoaderCache.cachedCustomClassloader(
      all.toList,
      () => new DottyLoader
    )
    new ScalaInstance(
      dottyVersion,
      fullLoader,
      libraryLoader,
      libraryJars,
      compiler,
      all.toArray,
      None)
  }
}

/**
 * The parent classloader of the Scala compiler.
 *
 * A TopClassLoader is constructed from the sbt classloader.
 *
 * To understand why a custom parent classloader is needed for the compiler,
 * let us describe some alternatives that wouldn't work.
 *
 * - `new URLClassLoader(urls)`:
 *   The compiler contains sbt phases that callback to sbt using the `xsbti.*`
 *   interfaces. If `urls` does not contain the sbt interfaces we'll get a
 *   `ClassNotFoundException` in the compiler when we try to use them, if
 *   `urls` does contain the interfaces we'll get a `ClassCastException` or a
 *   `LinkageError` because if the same class is loaded by two different
 *   classloaders, they are considered distinct by the JVM.
 *
 * - `new URLClassLoader(urls, sbtLoader)`:
 *    Because of the JVM delegation model, this means that we will only load
 *    a class from `urls` if it's not present in the parent `sbtLoader`, but
 *    sbt uses its own version of the scala compiler and scala library which
 *    is not the one we need to run the compiler.
 *
 * Our solution is to implement an URLClassLoader whose parent is
 * `new TopClassLoader(sbtLoader)`. We override `loadClass` to load the
 * `xsbti.*` interfaces from `sbtLoader`.
 *
 * The parent loader of the TopClassLoader is set to `null` so that the JDK
 * classes and only the JDK classes are loade from it.
 */
private class TopClassLoader(sbtLoader: ClassLoader) extends ClassLoader(null) {
  // We can't use the loadClass overload with two arguments because it's
  // protected, but we can do the same by hand (the classloader instance
  // from which we call resolveClass does not matter).
  // The one argument overload of loadClass delegates to this one.
  override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
    if (name.startsWith("xsbti.") || name.startsWith("org.jline.")) {
      val c = sbtLoader.loadClass(name)
      if (resolve) resolveClass(c)
      c
    }
    else super.loadClass(name, resolve)
  }
}


/** A class file manger that prunes .tasty as needed.
 *
 *  This makes sure that, when a .class file must be deleted, the
 *  corresponding .tasty file is also deleted.
 *
 *  This code is adapted from Zinc `TransactionalClassFileManager`.
 *  We need to duplicate the logic since forwarding to the default class
 *  file manager doesn't work: we need to backup tasty files in a different
 *  temporary directory as class files.
 *
 *  To support older versions of dotty, this also takes care of .hasTasty
 *  files, although they are not used anymore.
 */
final class TastyFileManager extends ClassFileManager {
  private[this] var _tempDir: File = null
  private[this] def tempDir = {
    if (_tempDir == null) {
      _tempDir = Files.createTempDirectory("backup").toFile
    }
    _tempDir
  }

  private[this] val generatedTastyFiles = new mutable.HashSet[File]
  private[this] val movedTastyFiles = new mutable.HashMap[File, File]

  override def delete(classes: Array[File]): Unit = {
    val tasties = tastyFiles(classes)
    val toBeBackedUp = tasties
      .filter(t => t.exists && !movedTastyFiles.contains(t) && !generatedTastyFiles(t))
    for (c <- toBeBackedUp)
      movedTastyFiles.put(c, move(c))
    IO.deleteFilesEmptyDirs(tasties)
  }

  override def generated(classes: Array[File]): Unit =
    generatedTastyFiles ++= tastyFiles(classes)

  override def complete(success: Boolean): Unit = {
    if (!success) {
      IO.deleteFilesEmptyDirs(generatedTastyFiles)
      for ((orig, tmp) <- movedTastyFiles) IO.move(tmp, orig)
    }

    generatedTastyFiles.clear()
    movedTastyFiles.clear()
    if (_tempDir != null) {
      IO.delete(tempDir)
      _tempDir = null
    }
  }

  private def tastyFiles(classes: Array[File]): Array[File] = {
    val tastySuffixes = List(".tasty", ".hasTasty")
    classes.flatMap { classFile =>
      if (classFile.getPath.endsWith(".class")) {
        val prefix = classFile.getAbsolutePath.stripSuffix(".class")
        tastySuffixes.map(suffix => new File(prefix + suffix)).filter(_.exists)
      } else Nil
    }
  }

  private def move(c: File): File = {
    val target = File.createTempFile("sbt", ".tasty", tempDir)
    IO.move(c, target)
    target
  }
}