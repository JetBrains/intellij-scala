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

import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VfsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.facet.ScalaFacet;
import org.jetbrains.plugins.scala.facet.ScalaFacetType;

import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;

/**
 * @author ilyas
 */
public class ScalaConfiguration implements ApplicationComponent, JDOMExternalizable, Configurable {

  private Library scalaLib;
  ScalaConfigurationEditor editor;

  public static final String UNDEFINED_VERSION = "undefined";
  public static final String SCALA_LIBRARY_NAME = "SCALA";
  public static final String PLUGIN_MODULE_ID = "PLUGIN_MODULE";


  protected String scalaPath = "";

  protected static final String SCALA_PATH = "scalaPath";


  public ScalaConfiguration() {

  }

  public static ScalaConfiguration getInstance() {
    return ApplicationManager.getApplication().getComponent(ScalaConfiguration.class);
  }


  public void readExternal(Element element) throws InvalidDataException {
    scalaPath = JDOMExternalizer.readString(element, SCALA_PATH);
    if (scalaPath == null)
      scalaPath = "";
    //todo  load version info
  }

  public void reset() {
    editor.getScalaPathField().setText(scalaPath);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    JDOMExternalizer.write(element, SCALA_PATH, scalaPath);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ScalaConfiguration";
  }

  public void initComponent() {
    FacetTypeRegistry.getInstance().registerFacetType(ScalaFacetType.INSTANCE);
    LibraryTable libTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    scalaLib = libTable.getLibraryByName(SCALA_LIBRARY_NAME);
  }

  public void disposeComponent() {
    FacetTypeRegistry instance = FacetTypeRegistry.getInstance();
    instance.unregisterFacetType(instance.findFacetType(ScalaFacet.FACET_TYPE_ID));
  }

  public void disposeUIResources() {
    editor = null;
  }

  @Nls
  public String getDisplayName() {
    return "Scala";
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    editor = new ScalaConfigurationEditor();
    return editor.getMainPanel();
  }

  public boolean isModified() {
    return !scalaPath.equals(getConfiguredGroovyPath());
  }

  public void apply() throws ConfigurationException {
    final String newGroovyPath = getConfiguredGroovyPath();
    if ("".equals(newGroovyPath)) {
      scalaPath = "";
    } else if (!isValidScalaPath(newGroovyPath)) {
      Messages.showErrorDialog(ScalaBundle.message("error.scala.path.not.valid"), ScalaBundle.message("error.external"));
      return;
    }


    if (!scalaPath.equals(newGroovyPath)) {
      scalaPath = newGroovyPath;
      createGroovyLibs();
    }

    // todo load Scala version info from jar
  }

  private void createGroovyLibs() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        Library.ModifiableModel model;

        if (scalaPath.length() > 0) {
          scalaLib = createLibFirstTime(SCALA_LIBRARY_NAME);
          model = getScalaLib().getModifiableModel();
          removeOldRoots(model);
          String srcPath = scalaPath + "/src";

          File[] jars;

          // add sources
          File srcDir = new File(srcPath);
          if (srcDir.exists()) {
            jars = srcDir.listFiles();
          } else {
            jars = new File(srcPath).listFiles();
          }
          if (jars != null)
            for (File file : jars)
              if (file.getName().endsWith(".jar"))
                model.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.SOURCES);


          String libPath = scalaPath + "/lib";
          File libDir = new File(libPath);
          if (libDir.exists()) {
            jars = libDir.listFiles();
          } else {
            jars = new File(libPath).listFiles();
          }
          if (jars != null)
            for (File file : jars)
              if (file.getName().endsWith(".jar"))
                model.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);

          model.commit();

          addLibraryToReferringModules(ScalaFacet.FACET_TYPE_ID, getScalaLib());

        } else {
          scalaLib = null;
        }
      }
    });
  }

  private static Library createLibFirstTime(String baseName) {
    LibraryTable libTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    Library library = libTable.getLibraryByName(baseName);
    if (library == null) {
      library = LibraryUtil.createLibrary(libTable, baseName);
    }
    return library;
  }


  private static void removeOldRoots(Library.ModifiableModel model) {
    for (OrderRootType type : ALL_TYPES)
      for (String url : model.getUrls(type))
        model.removeRoot(url, type);
  }

  private static final OrderRootType[] ALL_TYPES = {OrderRootType.CLASSES, OrderRootType.CLASSES_AND_OUTPUT, OrderRootType.COMPILATION_CLASSES, OrderRootType.SOURCES,};


  private String getConfiguredGroovyPath() {
    return editor.getScalaPathField().getText().trim();
  }

  private static void addLibraryToReferringModules(FacetTypeId<?> facetID, Library library) {
    for (Project prj : ProjectManager.getInstance().getOpenProjects())
      for (Module module : ModuleManager.getInstance(prj).getModules()) {
        if (FacetManager.getInstance(module).getFacetByType(facetID) != null) {
          addLibrary(library, module);
        }
      }
  }

  private static boolean isValidScalaPath(String path) {
    File scalaPathFile = new File(path);

    if (!scalaPathFile.exists()) return false;
    if (!scalaPathFile.isDirectory()) return false;

    File[] files = scalaPathFile.listFiles(new FilenameFilter() {
      public boolean accept(File file, String s) {
        return "bin".equals(s);
      }
    });

    if (files == null || files.length != 1) return false;

    File binDir = files[0];

    File[] binDirFiles = binDir.listFiles(new FilenameFilter() {
      public boolean accept(File file, String s) {
        return "scala".equals(s) || "scala.bat".equals(s);
      }
    });

    return !(binDirFiles == null || binDirFiles.length == 0);
  }


  public static void addLibrary(Library library, Module module) {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    if (!libraryReferenced(rootManager, library)) {
      final ModifiableRootModel moduleModel = rootManager.getModifiableModel();
      final LibraryOrderEntry addedEntry = moduleModel.addLibraryEntry(library);
      final OrderEntry[] order = moduleModel.getOrderEntries();

      //place library before jdk
      assert order[order.length - 1] == addedEntry;
      int insertionPoint = - -1;
      for (int i = 0; i < order.length - 1; i++) {
        if (order[i] instanceof JdkOrderEntry) {
          insertionPoint = i;
          break;
        }
      }
      if (insertionPoint >= 0) {
        for (int i = order.length - 1; i > insertionPoint; i--) {
          order[i] = order[i - 1];
        }
        order[insertionPoint] = addedEntry;

        moduleModel.rearrangeOrderEntries(order);
      }
      moduleModel.commit();
    }
  }

  public static boolean libraryReferenced(ModuleRootManager rootManager, Library library) {
    final OrderEntry[] entries = rootManager.getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry && library.equals(((LibraryOrderEntry) entry).getLibrary())) return true;
    }
    return false;
  }

  public String getScalaInstallPath() {
    return scalaPath;
  }

  public boolean isScalaConfigured() {
    return scalaPath != null && scalaPath.length() > 0;
  }

  public boolean isScalaConfigured(Module module) {
    return scalaPath.length() != 0 && (module == null || FacetManager.getInstance(module).getFacetByType(ScalaFacet.FACET_TYPE_ID) != null);
  }


  public Library getScalaLib() {
    return scalaLib;
  }
}
