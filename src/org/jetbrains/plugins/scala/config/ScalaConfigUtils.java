/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.config;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings;
import org.jetbrains.plugins.scala.util.LibrariesUtil;
import org.jetbrains.plugins.scala.util.ScalaUtils;

import java.io.File;
import java.io.IOException;
import java.io.FilenameFilter;
import java.util.Collection;
import java.util.Properties;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author ilyas
 */
public class ScalaConfigUtils {
  private static final String SCALA_MAIN_LIB = "scala-library.jar";
  public static final String UNDEFINED_VERSION = "undefined";
  public static final String SCALA_LIB_PREFIX = "scala-";

  public static final String SCALA_COMPILER_LIB_NAME = "scala-compiler.jar";
  public static final String SCALA_LIB_NAME = "scala-library.jar";
  public static final String SCALA_LIB_PATTERN = "scala-.*jar";

  public static final String LIBRARY_PROPERTIES_PATH = "library.properties";
  public static final String COMPILER_CLASS_PATH = "scala/tools/nsc/CompilerRun.class";
  public static final String PREFED_CLASS_PATH = "scala/Predef.class";
  public static final String VERSION_PROPERTY_KEY = "version.number";

  private static final Condition<Library> SCALA_LIB_CONDITION = new Condition<Library>() {
    public boolean value(Library library) {
      return isScalaSdkLibrary(library);
    }
  };


  public static boolean isScalaSdkHome(final VirtualFile file) {
    final Ref<Boolean> result = Ref.create(false);
    processFilesUnderScalaSDKRoot(file, new Processor<VirtualFile>() {
      public boolean process(final VirtualFile virtualFile) {
        result.set(true);
        return false;
      }
    });
    return result.get();
  }

  private static void processFilesUnderScalaSDKRoot(VirtualFile file, final Processor<VirtualFile> processor) {
    if (file != null && file.isDirectory()) {
      final VirtualFile child = file.findChild("lib");

      if (child != null && child.isDirectory()) {
        for (VirtualFile grandChild : child.getChildren()) {
          if (SCALA_MAIN_LIB.equals(grandChild.getNameWithoutExtension())) {
            if (!processor.process(grandChild)) return;
          }
        }
      }
    }
  }

  public static String getScalaVersion(@NotNull String path) {
    String jarVersion = getScalaJarVersion(path + "/lib", SCALA_LIB_NAME, LIBRARY_PROPERTIES_PATH);
    return jarVersion != null ? jarVersion : UNDEFINED_VERSION;
  }

  /**
   * Return value of Implementation-Version attribute in jar manifest
   * <p/>
   *
   * @param jarPath  directory containing jar file
   * @param jarRegex filename pattern for jar file
   * @param propPath path to properties file in jar file
   * @return value of Implementation-Version attribute, null if not found
   */
  public static String getScalaJarVersion(String jarPath, final String jarRegex, String propPath) {
    try {
      File[] jars = ScalaUtils.getFilesInDirectoryByPattern(jarPath, jarRegex);
      if (jars.length != 1) {
        return null;
      }
      JarFile jarFile = new JarFile(jars[0]);
      JarEntry jarEntry = jarFile.getJarEntry(propPath);
      if (jarEntry == null) {
        return null;
      }
      Properties properties = new Properties();
      properties.load(jarFile.getInputStream(jarEntry));
      return properties.getProperty(VERSION_PROPERTY_KEY);
    }
    catch (Exception e) {
      return null;
    }
  }

  public static Library[] getProjectScalaLibraries(Project project) {
    if (project == null) return new Library[0];
    final LibraryTable table = ProjectLibraryTable.getInstance(project);
    final List<Library> all = ContainerUtil.findAll(table.getLibraries(), SCALA_LIB_CONDITION);
    return all.toArray(new Library[all.size()]);
  }

  public static Library[] getAllScalaLibraries(@Nullable Project project) {
    return ArrayUtil.mergeArrays(getGlobalScalaLibraries(), getProjectScalaLibraries(project), Library.class);
  }

  public static Library[] getGlobalScalaLibraries() {
    return LibrariesUtil.getGlobalLibraries(SCALA_LIB_CONDITION);
  }

  public static String[] getScalaLibNames() {
    return LibrariesUtil.getLibNames(getGlobalScalaLibraries());
  }


  public static boolean isScalaSdkLibrary(Library library) {
    if (library == null) return false;
    VirtualFile[] classFiles = library.getFiles(OrderRootType.CLASSES);
    boolean hasLibJar = false;
    boolean hasCompilerJar = false;

    for (VirtualFile file : classFiles) {
      String path = file.getPath();
      if (path != null && "jar".equals(file.getExtension())) {
        path = StringUtil.trimEnd(path, "!/");
        String name = file.getName();

        File realFile = new File(path);
        if (realFile.exists()) {
          try {
            JarFile jarFile = new JarFile(realFile);
            if (SCALA_LIB_NAME.equals(name)) {
              hasLibJar = jarFile.getJarEntry(LIBRARY_PROPERTIES_PATH) != null && jarFile.getJarEntry(PREFED_CLASS_PATH) != null;
            } else if (SCALA_COMPILER_LIB_NAME.equals(name)) {
              hasCompilerJar = jarFile.getJarEntry(COMPILER_CLASS_PATH) != null;
            }
          } catch (IOException e) {
            return false;
          }
        }
      }
    }
    return hasCompilerJar && hasLibJar;
  }

  @Nullable
  public static String getScalaLibVersion(Library library) {
    return getScalaVersion(LibrariesUtil.getScalaLibraryHome(library));
  }

  public static ScalaSDK[] getScalaSDKs(final Module module) {
    final ScalaSDK[] projectSdks =
        ContainerUtil.map2Array(getProjectScalaLibraries(module.getProject()), ScalaSDK.class, new Function<Library, ScalaSDK>() {
          public ScalaSDK fun(final Library library) {
            return new ScalaSDK(library, module, true);
          }
        });
    final ScalaSDK[] globals = ContainerUtil.map2Array(getGlobalScalaLibraries(), ScalaSDK.class, new Function<Library, ScalaSDK>() {
      public ScalaSDK fun(final Library library) {
        return new ScalaSDK(library, module, false);
      }
    });
    return ArrayUtil.mergeArrays(globals, projectSdks, ScalaSDK.class);
  }

  public static void updateScalaLibInModule(@NotNull Module module, @Nullable ScalaSDK sdk) {
    ModuleRootManager manager = ModuleRootManager.getInstance(module);
    ModifiableRootModel model = manager.getModifiableModel();
    removeScalaLibrariesFormModule(model);
    if (sdk == null || sdk.getLibrary() == null) {
      model.commit();
      return;
    }

    saveScalaDefaultLibName(sdk.getLibraryName());
    Library newLib = sdk.getLibrary();
    LibraryOrderEntry addedEntry = model.addLibraryEntry(newLib);
    LibrariesUtil.placeEntryToCorrectPlace(model, addedEntry);
    model.commit();
  }

  public static void removeScalaLibrariesFormModule(ModifiableRootModel model) {
    OrderEntry[] entries = model.getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libEntry = (LibraryOrderEntry) entry;
        Library library = libEntry.getLibrary();
        if (isScalaSdkLibrary(library)) {
          model.removeOrderEntry(entry);
        }
      }
    }
  }

  public static Library[] getScalaLibrariesByModule(final Module module) {
    final Condition<Library> condition = new Condition<Library>() {
      public boolean value(Library library) {
        return isScalaSdkLibrary(library);
      }
    };
    return LibrariesUtil.getLibrariesByCondition(module, condition);
  }

  public static ValidationResult isScalaSdkHome(String path) {
    if (path != null) {
      final VirtualFile relativeFile = VfsUtil.findRelativeFile(path, null);
      if (relativeFile != null && ScalaConfigUtils.isScalaSdkHome(relativeFile)) {
        return ValidationResult.OK;
      }
    }
    return new ValidationResult(ScalaBundle.message("invalid.scala.sdk.path.message"));
  }

  @Nullable
  public static Library createScalaLibrary(final String path,
                                           final String name,
                                           final Project project,
                                           final boolean inModuleSettings,
                                           final boolean inProject) {
    if (project == null) return null;
    final Ref<Library> libRef = new Ref<Library>();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        Library library = createScalaLibImmediately(path, name, project, inModuleSettings, inProject);
        libRef.set(library);
      }
    });
    return libRef.get();
  }


  private static Library createScalaLibImmediately(String path,
                                                   String name,
                                                   Project project,
                                                   boolean inModuleSettings,
                                                   final boolean inProject) {
    String version = getScalaVersion(path);
    String libName = name != null ? name : generateNewScalaLibName(version, project);
    if (path.length() > 0) {
      // create library
      LibraryTable.ModifiableModel modifiableModel = null;
      Library library;

      if (inModuleSettings) {
        StructureConfigurableContext context = ModuleStructureConfigurable.getInstance(project).getContext();
        LibraryTableModifiableModelProvider provider = context
            .createModifiableModelProvider(inProject ? LibraryTablesRegistrar.PROJECT_LEVEL : LibraryTablesRegistrar.APPLICATION_LEVEL, true);
        modifiableModel = provider.getModifiableModel();
        library = modifiableModel.createLibrary(libName);
      } else {
        LibraryTable libTable =
            inProject ? ProjectLibraryTable.getInstance(project) : LibraryTablesRegistrar.getInstance().getLibraryTable();
        library = libTable.getLibraryByName(libName);
        if (library == null) {
          library = LibraryUtil.createLibrary(libTable, libName);
        }
      }

      // fill library
      final Library.ModifiableModel model;
      if (inModuleSettings) {
        model = ((LibrariesModifiableModel) modifiableModel).getLibraryEditor(library).getModel();
      } else {
        model = library.getModifiableModel();
      }

      FilenameFilter scalaJarFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return name != null && name.matches(SCALA_LIB_PATTERN);
        }
      };

      // add sources
      File srcRoot = new File(path + "/src");
      if (srcRoot.exists()) {
        File[] files = srcRoot.listFiles(scalaJarFilter);
        for (File file : files) {
          model.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.SOURCES);
        }
      }

      // add classfiles
      File libDir = new File(path + "/lib");
      if (libDir.exists()) {
        File[] jars = libDir.listFiles(scalaJarFilter);
        for (File file : jars) {
          if (file.getName().endsWith(".jar")) {
            model.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);
          }
        }
      }

      if (!inModuleSettings) {
        model.commit();
      }
      return library;
    }
    return null;
  }

  public static String generateNewScalaLibName(String version, final Project project) {
    String prefix = SCALA_LIB_PREFIX;
    return LibrariesUtil.generateNewLibraryName(version, prefix, project);
  }

  public static void saveScalaDefaultLibName(String name) {
    ScalaApplicationSettings settings = ScalaApplicationSettings.getInstance();
    if (!UNDEFINED_VERSION.equals(name)) {
      settings.DEFAULT_SCALA_LIB_NAME = name;
    }
  }

  @Nullable
  public static String getScalaDefaultLibName() {
    ScalaApplicationSettings settings = ScalaApplicationSettings.getInstance();
    return settings.DEFAULT_SCALA_LIB_NAME;
  }

  public static void removeOldRoots(Library.ModifiableModel model) {
    for (OrderRootType type : OrderRootType.getAllTypes())
      for (String url : model.getUrls(type))
        model.removeRoot(url, type);
  }

  public static Library createLibFirstTime(String baseName) {
    LibraryTable libTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    Library library = libTable.getLibraryByName(baseName);
    if (library == null) {
      library = LibraryUtil.createLibrary(libTable, baseName);
    }
    return library;
  }


  public static Collection<String> getScalaVersions(final Module module) {
    return getScalaVersions(module.getProject());
  }

  public static Collection<String> getScalaVersions(final Project project) {
    return ContainerUtil.map2List(getAllScalaLibraries(project), new Function<Library, String>() {
      public String fun(Library library) {
        return getScalaLibVersion(library);
      }
    });
  }

  public static boolean isScalaConfigured(Module module) {
    return module != null && getScalaLibrariesByModule(module).length > 0;
  }

  @NotNull
  public static String getScalaInstallPath(Module module) {
    if (module == null) return "";
    Library[] libraries = getScalaLibrariesByModule(module);
    if (libraries.length == 0) return "";
    Library library = libraries[0];
    return LibrariesUtil.getScalaLibraryHome(library);
  }

  public static void setUpScalaFacet(final ModifiableRootModel model) {
    LibraryTable libTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    final Project project = model.getModule().getProject();
    String name = ScalaApplicationSettings.getInstance().DEFAULT_SCALA_LIB_NAME;
    if (name != null && libTable.getLibraryByName(name) != null) {
      Library library = libTable.getLibraryByName(name);
      if (isScalaSdkLibrary(library)) {
        LibraryOrderEntry entry = model.addLibraryEntry(library);
        LibrariesUtil.placeEntryToCorrectPlace(model, entry);
      }
    } else {
      final Library[] libraries = getAllScalaLibraries(project);
      if (libraries.length > 0) {
        Library library = libraries[0];
        if (isScalaSdkLibrary(library)) {
          LibraryOrderEntry entry = model.addLibraryEntry(library);
          LibrariesUtil.placeEntryToCorrectPlace(model, entry);
        }
      }
    }
  }

}
