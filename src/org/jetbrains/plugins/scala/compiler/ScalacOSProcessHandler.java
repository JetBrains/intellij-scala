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
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashSet;

import javax.swing.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Set;

/**
 * @author ven
 */
public class ScalacOSProcessHandler extends OSProcessHandler {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.scala.compiler.ScalacOSProcessHandler");
  private CompileContext myContext;
  private Project myProject;
  private Integer myLineNumber;
  private String myErrMessage;
  private String myUrl;

  private Set<TranslatingCompiler.OutputItem> mySuccessfullyCompiledSources;

  public Set<TranslatingCompiler.OutputItem> getSuccessfullyCompiled() {
    return Collections.unmodifiableSet(mySuccessfullyCompiledSources);
  }

  public ScalacOSProcessHandler(GeneralCommandLine commandLine, CompileContext context, Project project) throws ExecutionException {
    super(commandLine.createProcess(), commandLine.getCommandLineString());
    myContext = context;
    myProject = project;
    mySuccessfullyCompiledSources = new HashSet<TranslatingCompiler.OutputItem>();
  }


  public void notifyTextAvailable(final String text, final Key outputType) {
    super.notifyTextAvailable(text, outputType);
    parseOutput(text);
  }

  private static final String ourErrorMarker = " error:";
  private static final String ourInfoMarkerStart = "[";
  private static final String ourInfoMarkerEnd = "]";
  private static final String ourWroteMarker = "wrote ";
  private static final String ourColumnMarker = "^";
  private static final String ourParsingMarker = "parsing";

  // Phases
  public static String PHASE = "running phase ";
//  private static final String ourTyperPhaseMarker = PHASE + "typer on";
//  private static final String ourSuperAccMarker = PHASE + "superaccessors on";
//  private static final String ourPicklerMarker = PHASE + "pickler on";
//  private static final String ourRefcheckMarker = PHASE + "refcheck on";
//  private static final String ourLiftcodeMarker = PHASE + "liftcode on";

  private boolean mustProcessErrorMsg = false;
  private int myErrColumnMarker;


  private void parseOutput(String text) {

    text = text.trim();
    if (text.endsWith("\r\n")) text = text.substring(0, text.length() - 2);

    // Add error message to output
    if (myErrMessage != null && stopMsgProcessing(text) && mustProcessErrorMsg) {
      myErrColumnMarker = myErrColumnMarker > 0 ? myErrColumnMarker : 1;
      myContext.addMessage(CompilerMessageCategory.ERROR, myErrMessage, myUrl, myLineNumber, myErrColumnMarker);
      myErrMessage = null;
      mustProcessErrorMsg = false;
    }

    if (text.indexOf(ourErrorMarker) > 0) { // new error occurred
      processErrorMesssage(text, text.indexOf(ourErrorMarker));
    } else if (!text.startsWith(ourInfoMarkerStart)) { //  continuing process [error] message
      if (mustProcessErrorMsg) {
        if (ourColumnMarker.equals(text.trim())) {
          myErrColumnMarker = text.indexOf(ourColumnMarker) + 1;
        } else if (myErrMessage != null) {
          myErrMessage += "\n" + text;
        } else {
          mustProcessErrorMsg = false;
        }
      }
    } else { //verbose compiler output
      mustProcessErrorMsg = false;
      String info = text.endsWith(ourInfoMarkerEnd) ?
              text.substring(ourInfoMarkerStart.length(), text.length() - ourInfoMarkerEnd.length()) :
              text.substring(ourInfoMarkerStart.length());
      if (info.startsWith(ourParsingMarker)) { //parsing
        myContext.getProgressIndicator().setText(info);
      } else if (info.startsWith(PHASE)) { // typechecker phase
        myContext.getProgressIndicator().setText(StringUtil.trimStart(info, PHASE));
      } else if (info.startsWith(ourWroteMarker)) {
        myContext.getProgressIndicator().setText(info);
        String s = info.substring(ourWroteMarker.length());
        int w = s.indexOf(' ');
        String outputPath = w > 0 ? s.substring(0, w) : s;
        try {
          TranslatingCompiler.OutputItem item = getOutputItem(outputPath.replace(File.separatorChar, '/'));
          if (item != null) {
            mySuccessfullyCompiledSources.add(item);
          }
        } catch (InvocationTargetException e) {
          // Normal behaviior
        } catch (InterruptedException e) {
        }
      }
    }
  }

  private boolean stopMsgProcessing(String text) {
    return text.startsWith(ourInfoMarkerStart) && !text.trim().equals(ourColumnMarker) || text.indexOf(ourErrorMarker) > 0;
  }

  /*
  Collect information about error occurrence
   */
  private void processErrorMesssage(String text, int errIndex) {
    String errorPlace = text.substring(0, errIndex);
    if (errorPlace.endsWith(":"))
      errorPlace = errorPlace.substring(0, errorPlace.length() - 1); //hack over compiler output
    int j = errorPlace.lastIndexOf(':');
    if (j > 0) {
      myUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, errorPlace.substring(0, j).replace(File.separatorChar, '/'));
      try {
        myLineNumber = Integer.valueOf(errorPlace.substring(j + 1, errorPlace.length()));
        myErrMessage = text.substring(errIndex + 1).trim();
        mustProcessErrorMsg = true;
      } catch (NumberFormatException e) {
        myContext.addMessage(CompilerMessageCategory.INFORMATION, "", text, -1, -1);
        myErrMessage = null;
        mustProcessErrorMsg = false;
      }
    } else {
      myContext.addMessage(CompilerMessageCategory.INFORMATION, "", text, -1, -1);
    }
  }


  private TranslatingCompiler.OutputItem getOutputItem(final String outputPath) throws InvocationTargetException, InterruptedException {
    final Application application = ApplicationManager.getApplication();
    final TranslatingCompiler.OutputItem[] result = new TranslatingCompiler.OutputItem[1];
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        result[0] = application.runWriteAction(new Computable<TranslatingCompiler.OutputItem>() {
          public TranslatingCompiler.OutputItem compute() {
            VirtualFile classFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(outputPath.replace(File.separatorChar, '/'));
            LOG.assertTrue(classFile != null);
            final VirtualFile outputRoot = getOutputRoot(classFile);
            final String fileName = VfsUtil.getRelativePath(classFile, outputRoot, '.');
            assert fileName.endsWith(".class");
            final String fqName = fileName.substring(0, fileName.length() - ".class".length());
            final PsiManager manager = PsiManager.getInstance(myProject);
            final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
            PsiClass aClass = application.runReadAction(new Computable<PsiClass>() {
              public PsiClass compute() {
                return facade.findClass(fqName, GlobalSearchScope.projectScope(myProject));
              }
            });
            if (aClass == null || !aClass.isValid()) return null;
            final VirtualFile sourceFile = aClass.getContainingFile().getVirtualFile();
            return new TranslatingCompiler.OutputItem() {
              public String getOutputPath() {
                return outputPath;
              }

              public VirtualFile getSourceFile() {
                return sourceFile;
              }

              public String getOutputRootDirectory() {
                return outputRoot.getPath();
              }
            };
          }
        });
      }
    });
    return result[0];
  }

  private VirtualFile getOutputRoot(VirtualFile classFile) {
    final VirtualFile[] outputs = myContext.getAllOutputDirectories();
    for (final VirtualFile output : outputs) {
      if (VfsUtil.isAncestor(output, classFile, true)) {
        return output;
      }
    }

    LOG.assertTrue(false);
    return null;
  }
}
