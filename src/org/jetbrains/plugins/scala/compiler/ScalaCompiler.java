package org.jetbrains.plugins.scala.compiler;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.project.Project;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.sdk.ScalaSdkType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author ven
 */
public class ScalaCompiler implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.scala.compiler.ScalaCompiler");
  private static final String CLASS_PATH_LIST_SEPARATOR = SystemInfo.isWindows ? ";" : ":";
  private Project myProject;

  public ScalaCompiler(Project project) {
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

  private static final String SCALAC_RUNNER_QUALIFIED_NAME = "org.jetbrains.plugins.scala.compiler.rt.ScalacRunner";

  public ExitStatus compile(CompileContext compileContext, final VirtualFile[] virtualFiles) {
    Map<Module, Set<VirtualFile>> map = buildModuleToFilesMap(compileContext, virtualFiles);
    Set<OutputItem> compiledItems = new HashSet<OutputItem>();
    Set<VirtualFile> allCompiling = new HashSet<VirtualFile>();
    for (Map.Entry<Module, Set<VirtualFile>> entry : map.entrySet()) {
      Module module = entry.getKey();
      Set<VirtualFile> files = entry.getValue();
      allCompiling.addAll(files);
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      ProjectJdk sdk = rootManager.getJdk();
      assert sdk != null && sdk.getSdkType() instanceof ScalaSdkType;
      String scalaCompilerPath = ((ScalaSdkType) sdk.getSdkType()).getScalaCompilerPath(sdk);
      String javaExe = sdk.getVMExecutablePath();
      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(javaExe);
      commandLine.addParameter("-cp");
      String myJarPath = PathUtil.getJarPathForClass(getClass());
      commandLine.addParameter(new StringBuilder().append(myJarPath).
                                                   append(CLASS_PATH_LIST_SEPARATOR).
                                                   append(scalaCompilerPath).toString());

      commandLine.addParameter(SCALAC_RUNNER_QUALIFIED_NAME);

      try {
        File f = File.createTempFile("toCompile", "");
        PrintStream printer = new PrintStream(new FileOutputStream(f));

        printer.println("-Xgenerics");
        printer.println("-Xexperimental");
        printer.println("-target:jvm-1.5");
        printer.println("-verbose");

        //write output dir
        String url = rootManager.getCompilerOutputPathUrl();
        LOG.assertTrue(url != null);
        String outputPath = VirtualFileManager.extractPath(url);
        printer.println("-d");
        printer.println(outputPath);

        //write classpath
        OrderEntry[] entries = rootManager.getOrderEntries();
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
          if (i < filesArray.length - 1) {
            printer.print(CLASS_PATH_LIST_SEPARATOR);
          }
        }

        printer.println();

        for (VirtualFile file : files) {
          printer.println(file.getPath());
        }

        printer.close();

        commandLine.addParameter(f.getPath());
        final ScalacOSProcessHandler processHandler = new ScalacOSProcessHandler(commandLine, compileContext, myProject);
        processHandler.startNotify();
        processHandler.waitFor();
        compiledItems.addAll(processHandler.getSuccessfullyCompiled());
      } catch (IOException e) {
        LOG.error (e);
        return new RecompileExitStatus(virtualFiles);
      } catch (ExecutionException e) {
        LOG.error (e);
        return new RecompileExitStatus(virtualFiles);
      }
    }

    VirtualFile[] toRecompile = compiledItems.size() == allCompiling.size() ?
        VirtualFile.EMPTY_ARRAY :
        allCompiling.toArray(new VirtualFile[allCompiling.size()]);

    return new ScalaCompileExitStatus(compiledItems, toRecompile);
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

  @NotNull
  public String getDescription() {
    return "Scala compiler";
  }

  public boolean validateConfiguration(CompileScope compileScope) {
    VirtualFile[] scalaFiles = compileScope.getFiles(ScalaFileType.SCALA_FILE_TYPE, true);
    if (scalaFiles.length == 0) return true;
    Module[] modules = compileScope.getAffectedModules();
    for (Module module : modules) {
      ProjectJdk jdk = ModuleRootManager.getInstance(module).getJdk();
      if (jdk == null || !(jdk.getSdkType() instanceof ScalaSdkType)) {
        Messages.showErrorDialog("Cannot compile scala files.\nPlease set up scala sdk", "Cannot compile");
        return false;
      }

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
}