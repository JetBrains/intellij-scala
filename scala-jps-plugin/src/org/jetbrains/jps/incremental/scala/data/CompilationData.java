package org.jetbrains.jps.incremental.scala.data;

import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.scala.ConfigurationException;
import org.jetbrains.jps.incremental.scala.SettingsManager;
import org.jetbrains.jps.incremental.scala.model.CompilerLibraryHolder;
import org.jetbrains.jps.incremental.scala.model.FacetSettings;
import org.jetbrains.jps.incremental.scala.model.LibraryLevel;
import org.jetbrains.jps.incremental.scala.model.ProjectSettings;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Pavel Fatin
 */
public class CompilationData {
  private File myCompilerJar;
  private File myLibraryJar;
  private File[] myExtraJars;
  private String[] myCompilerOptions;
  private File myOutputDirectory;
  private File[] myCompilationClasspath;
  private boolean myScalaFirst;

  private CompilationData(File compilerJar,
                          File libraryJar,
                          File[] extraJars,
                          String[] compilerOptions,
                          File outputDirectory,
                          File[] compilationClasspath,
                          boolean scalaFirst) {
    myCompilerJar = compilerJar;
    myLibraryJar = libraryJar;
    myExtraJars = extraJars;
    myCompilerOptions = compilerOptions;
    myOutputDirectory = outputDirectory;
    myCompilationClasspath = compilationClasspath;
    myScalaFirst = scalaFirst;
  }

  public File getCompilerJar() {
    return myCompilerJar;
  }

  public File getLibraryJar() {
    return myLibraryJar;
  }

  public File[] getExtraJars() {
    return myExtraJars;
  }

  public String[] getCompilerOptions() {
    return myCompilerOptions;
  }

  public File getOutputDirectory() {
    return myOutputDirectory;
  }

  public File[] getCompilationClasspath() {
    return myCompilationClasspath;
  }

  public boolean isScalaFirst() {
    return myScalaFirst;
  }

  public static CompilationData create(CompileContext context, ModuleChunk chunk) {
    JpsModule module = chunk.representativeTarget().getModule();
    JpsModel model = context.getProjectDescriptor().getModel();

    // Find a Scala compiler library that is configured in a Scala facet
    JpsLibrary compilerLibrary = getCompilerLibraryIn(module, model);

    // Collect all files in the compiler library
    Collection<File> compilerClasspath = compilerLibrary.getFiles(JpsOrderRootType.COMPILED);
    if (compilerClasspath.isEmpty()) {
      throw new ConfigurationException("Scala compiler library is empty: " + compilerLibrary.getName());
    }

    File compilerJar = null;
    File libraryJar = null;
    Collection<File> extraJars = new ArrayList<File>();
    for (File file : compilerClasspath) {
      String name = file.getName();
      if ("scala-library.jar".equals(name)) {
        libraryJar = file;
      } else if ("scala-compiler.jar".equals(name)) {
        compilerJar = file;
      } else {
        extraJars.add(file);
      }
    }

    ModuleBuildTarget target = chunk.representativeTarget();

    if (compilerJar == null) {
      throw new ConfigurationException("No scala-compiler.jar in Scala compiler library in " + target.getModuleName());
    }

    if (libraryJar == null) {
      throw new ConfigurationException("No scala-compiler.jar in Scala compiler library in " + target.getModuleName());
    }

    // Get an output directory
    File outputDirectory = target.getOutputDir();
    if (outputDirectory == null) {
      throw new ConfigurationException("Output directory not specified for module " + target.getModuleName());
    }

    // Get compilation classpath files
    Collection<File> chunkClasspath = context.getProjectPaths()
        .getCompilationClasspathFiles(chunk, chunk.containsTests(), false, false);

    FacetSettings facet = SettingsManager.getFacetSettings(module);
    String[] compilerOptions = facet.getCompilerOptions();

    ProjectSettings projectSettings = SettingsManager.getProjectSettings(model.getProject());
    boolean scalaFirst = projectSettings.isScalaFirst();

    return new CompilationData(compilerJar, libraryJar, extraJars.toArray(new File[extraJars.size()]),
        compilerOptions, outputDirectory, chunkClasspath.toArray(new File[chunkClasspath.size()]), scalaFirst);
  }

  private static JpsLibrary getCompilerLibraryIn(JpsModule module, JpsModel model) {
    FacetSettings facet = SettingsManager.getFacetSettings(module);

    if (facet == null) {
      throw new ConfigurationException("No Scala facet in module " + module.getName());
    }

    JpsProject project = model.getProject();
    ProjectSettings projectSettings = SettingsManager.getProjectSettings(project);

    boolean fsc = facet.isFscEnabled();

    // Use either a facet compiler library or a project FSC compiler library
    CompilerLibraryHolder libraryHolder = fsc ? projectSettings : facet;

    LibraryLevel compilerLibraryLevel = libraryHolder.getCompilerLibraryLevel();

    if (compilerLibraryLevel == null) {
      String message = fsc
          ? "No FSC compiler library level set in project " + project.getName()
          : "No compiler library level set in module " + module.getName();
      throw new ConfigurationException(message);
    }

    JpsLibraryCollection libraryCollection = getLibraryCollection(compilerLibraryLevel, model, module);

    String compilerLibraryName = libraryHolder.getCompilerLibraryName();

    if (compilerLibraryName == null) {
      String message = fsc
          ? "No FSC compiler library name set in project " + project.getName()
          : "No compiler library name set in module " + module.getName();
      throw new ConfigurationException(message);
    }

    JpsLibrary library = libraryCollection.findLibrary(compilerLibraryName);

    if (library == null) {
      String message = fsc
          ? String.format("FSC compiler library in project %s not found: %s / %s ",
          project.getName(), compilerLibraryLevel, compilerLibraryName)
          : String.format("Сompiler library for module %s not found: %s / %s ",
          module.getName(), compilerLibraryLevel, compilerLibraryName);
      throw new ConfigurationException(message);
    }

    return library;
  }

  private static JpsLibraryCollection getLibraryCollection(LibraryLevel level, JpsModel model, JpsModule module) {
    switch (level) {
      case Global:
        return model.getGlobal().getLibraryCollection();
      case Project:
        return model.getProject().getLibraryCollection();
      case Module:
        return module.getLibraryCollection();
      default:
        throw new ConfigurationException("Unknown library level: " + level);
    }
  }
}
