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

package org.jetbrains.plugins.scala.compiler;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.compiler.rt.ScalacRunner;
import org.jetbrains.plugins.scala.config.ScalaConfigUtils;

import java.io.*;
import java.util.*;

/**
 * @author ven
 */
public class ScalaCompilerOld implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.scala.compiler.ScalaCompilerOld");
  private Project myProject;

  // VM properties
  private static final String XMX_COMPILER_PROPERTY = "-Xmx300m";
  private final String XSS_COMPILER_PROPERTY = "-Xss128m";

  // Scalac parameters           
  private static final String DEBUG_INFO_LEVEL_PROPEERTY = "-g:vars";
  private static final String VERBOSE_PROPERTY = "-verbose";
  private static final String DESTINATION_COMPILER_PROPERTY = "-d";
  private static final String DEBUG_PROPERTY = "-Ydebug";
  private static final String WARNINGS_PROPERTY = "-unchecked";

  public ScalaCompilerOld(Project project) {
    myProject = project;
  }

  public boolean isCompilableFile(VirtualFile virtualFile, CompileContext compileContext) {
    return ScalaFileType.SCALA_FILE_TYPE.equals(virtualFile.getFileType());
  }

  class ScalaCompileExitStatus implements ExitStatus {
    private OutputItem[] myCompiledItems;
    private VirtualFile[] myToRecompile;

    public ScalaCompileExitStatus(Set<OutputItem> compiledItems, VirtualFile[] toRecompile) {
      myToRecompile = toRecompile;
      myCompiledItems = compiledItems.toArray(new OutputItem[compiledItems.size()]);
    }

    public OutputItem[] getSuccessfullyCompiled() {
      return myCompiledItems;
    }

    public VirtualFile[] getFilesToRecompile() {
      return myToRecompile;
    }
  }


  public ExitStatus compile(CompileContext compileContext, final VirtualFile[] virtualFiles) {
    Set<OutputItem> successfullyCompiled = new HashSet<OutputItem>();
    Set<VirtualFile> allCompiling = new HashSet<VirtualFile>();

    Map<Module, Set<VirtualFile>> mapModulesToVirtualFiles = buildModuleToFilesMap(compileContext, virtualFiles);

    for (Map.Entry<Module, Set<VirtualFile>> entry : mapModulesToVirtualFiles.entrySet()) {
      GeneralCommandLine commandLine = new GeneralCommandLine();
      Module module = entry.getKey();

      Set<VirtualFile> files = entry.getValue();
      allCompiling.addAll(files);

      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      Sdk sdk = moduleRootManager.getSdk();
      assert sdk != null && sdk.getSdkType() instanceof JavaSdkType;

      String javaExecutablePath = ((JavaSdkType) sdk.getSdkType()).getVMExecutablePath(sdk);
      commandLine.setExePath(javaExecutablePath);

      commandLine.addParameter(XSS_COMPILER_PROPERTY);
      commandLine.addParameter(XMX_COMPILER_PROPERTY);
      commandLine.addParameter("-cp");

//      commandLine.addParameter("-Xnoagent");
//      commandLine.addParameter("-Djava.compiler=NONE");
//      commandLine.addParameter("-Xdebug");
//      commandLine.addParameter("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=2239");

      String rtJarPath = PathUtil.getJarPathForClass(ScalacRunner.class);
      final StringBuilder classPathBuilder = new StringBuilder();
      classPathBuilder.append(rtJarPath);
      classPathBuilder.append(File.pathSeparator);

      String scalaPath = ScalaConfigUtils.getScalaInstallPath(module);
      String libPath = (scalaPath + "/lib").replace(File.separatorChar, '/');

      VirtualFile lib = LocalFileSystem.getInstance().findFileByPath(libPath);
      if (lib != null) {
        for (VirtualFile file : lib.getChildren()) {
          if (required(file.getName())) {
            classPathBuilder.append(file.getPath());
            classPathBuilder.append(File.pathSeparator);
          }
        }
      }

      commandLine.addParameter(classPathBuilder.toString());
      commandLine.addParameter(ScalacRunner.class.getName());

      try {
        File fileWithParams = File.createTempFile("toCompile", "");
        fillFileWithScalacParams(module, files, fileWithParams);

        commandLine.addParameter(fileWithParams.getPath());
      } catch (IOException e) {
        LOG.error(e);
      }

      final ScalacOSProcessHandler processHandler;

      try {
        processHandler = new ScalacOSProcessHandler(commandLine, compileContext, myProject);
        processHandler.startNotify();
        processHandler.waitFor();
        successfullyCompiled.addAll(processHandler.getSuccessfullyCompiled());

      } catch (ExecutionException e) {
        LOG.error(e);
      }

    }

    VirtualFile[] toRecompile = successfullyCompiled.size() > 0 ? VirtualFile.EMPTY_ARRAY : allCompiling.toArray(new VirtualFile[allCompiling.size()]);
    return new ScalaCompileExitStatus(successfullyCompiled, toRecompile);
  }

  private void fillFileWithScalacParams(Module module, Set<VirtualFile> files, File fileWithParameters)
          throws FileNotFoundException {

    PrintStream printer = new PrintStream(new FileOutputStream(fileWithParameters));

    printer.println(VERBOSE_PROPERTY);
    printer.println(DEBUG_PROPERTY);
    printer.println(WARNINGS_PROPERTY);
    printer.println(DEBUG_INFO_LEVEL_PROPEERTY);

    //write output dir
    CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
    VirtualFile virtualFile = compilerModuleExtension.getCompilerOutputPath();

    LOG.assertTrue(virtualFile != null);

    String outputPath = VirtualFileManager.extractPath(virtualFile.getUrl());
    printer.println(DESTINATION_COMPILER_PROPERTY);
    printer.println(outputPath);

    //write classpath
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    OrderEntry[] entries = moduleRootManager.getOrderEntries();
    Set<VirtualFile> cpVFiles = new HashSet<VirtualFile>();
    for (OrderEntry orderEntry : entries) {
      cpVFiles.addAll(Arrays.asList(orderEntry.getFiles(OrderRootType.COMPILATION_CLASSES)));
    }

    printer.println("-cp");
    VirtualFile[] filesArray = cpVFiles.toArray(new VirtualFile[cpVFiles.size()]);
    for (int i = 0; i < filesArray.length; i++) {
      VirtualFile file = filesArray[i];
      String path = file.getPath();
      int jarSeparatorIndex = path.indexOf(JarFileSystem.JAR_SEPARATOR);
      if (jarSeparatorIndex > 0) {
        path = path.substring(0, jarSeparatorIndex);
      }
      printer.print(path);
      printer.print(File.pathSeparator);
    }

    printer.println();

    for (VirtualFile file : files) {
      printer.println(file.getPath());
    }

    printer.close();
  }

  @NotNull
  public String getDescription() {
    return "Scala compiler";
  }

  public boolean validateConfiguration(CompileScope compileScope) {
    VirtualFile[] files = compileScope.getFiles(ScalaFileType.SCALA_FILE_TYPE, true);
    if (files.length == 0) return true;

    final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    Set<Module> modules = new HashSet<Module>();
    for (VirtualFile file : files) {
      Module module = index.getModuleForFile(file);
      if (module != null) {
        modules.add(module);
      }
    }

    for (Module module : modules) {
      final String installPath = ScalaConfigUtils.getScalaInstallPath(module);
      if (installPath.length() == 0 && module.getModuleType() instanceof JavaModuleType) {
        Messages.showErrorDialog(myProject, ScalaBundle.message("cannot.compile.scala.files.no.facet"), ScalaBundle.message("cannot.compile"));
        return false;
      }
    }

    Set<Module> nojdkModules = new HashSet<Module>();
    for (Module module : compileScope.getAffectedModules()) {
      if (!(module.getModuleType() instanceof JavaModuleType)) continue;
      Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk == null || !(sdk.getSdkType() instanceof JavaSdkType)) {
        nojdkModules.add(module);
      }
    }

    if (!nojdkModules.isEmpty()) {
      final Module[] noJdkArray = nojdkModules.toArray(new Module[nojdkModules.size()]);
      if (noJdkArray.length == 1) {
        Messages.showErrorDialog(myProject, ScalaBundle.message("cannot.compile.scala.files.no.sdk", noJdkArray[0].getName()), ScalaBundle.message("cannot.compile"));
      } else {
        StringBuffer modulesList = new StringBuffer();
        for (int i = 0; i < noJdkArray.length; i++) {
          if (i > 0) modulesList.append(", ");
          modulesList.append(noJdkArray[i].getName());
        }
        Messages.showErrorDialog(myProject, ScalaBundle.message("cannot.compile.scala.files.no.sdk.mult", modulesList.toString()), ScalaBundle.message("cannot.compile"));
      }
      return false;
    }

    return true;
  }

  private static class RecompileExitStatus implements ExitStatus {

    private final VirtualFile[] myVirtualFiles;

    public RecompileExitStatus(VirtualFile[] virtualFiles) {
      myVirtualFiles = virtualFiles;
    }

    public OutputItem[] getSuccessfullyCompiled() {
      return new OutputItem[0];
    }

    public VirtualFile[] getFilesToRecompile() {
      return myVirtualFiles;
    }

  }


  static HashSet<String> required = new HashSet<String>();

  static {
    required.add("scala");
    required.add("sbaz");
  }

  private static boolean required(String name) {
    name = name.toLowerCase();
    if (!name.endsWith(".jar"))
      return false;

    name = name.substring(0, name.lastIndexOf('.'));
    int ind = name.lastIndexOf('-');
    if (ind != -1 && name.length() > ind + 1 && Character.isDigit(name.charAt(ind + 1))) {
      name = name.substring(0, ind);
    }

    for (String requiredStr : required) {
      if (name.contains(requiredStr)) return true;
    }

    return false;
  }


  private static Map<Module, Set<VirtualFile>> buildModuleToFilesMap(final CompileContext context, final VirtualFile[] files) {
    final Map<Module, Set<VirtualFile>> map = new HashMap<Module, Set<VirtualFile>>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (VirtualFile file : files) {
          final Module module = context.getModuleByFile(file);

          if (module == null) {
            continue; // looks like file invalidated
          }

          Set<VirtualFile> moduleFiles = map.get(module);
          if (moduleFiles == null) {
            moduleFiles = new HashSet<VirtualFile>();
            map.put(module, moduleFiles);
          }
          moduleFiles.add(file);
        }
      }
    });
    return map;
  }
}