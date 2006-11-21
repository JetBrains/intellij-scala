package org.jetbrains.plugins.scala.compiler;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.LocalFileSystem;

import java.io.File;

/**
 * @author ven
 */
public class ScalacOSProcessHandler extends OSProcessHandler {
  private CompileContext myContext;
  private boolean mySkipNextLine;

  public ScalacOSProcessHandler(GeneralCommandLine commandLine, CompileContext context) throws ExecutionException {
    super(commandLine.createProcess(), commandLine.getCommandLineString());
    myContext = context;
  }


  public void notifyTextAvailable(final String text, final Key outputType) {
    super.notifyTextAvailable(text, outputType);
    parseOutput(text);
  }

  private static final String ourErrorMarker = " error:";


  private void parseOutput(String text) {
    if (mySkipNextLine) {
      mySkipNextLine = false;
      return;
    }

    text = text.trim();
    if (text.endsWith("\r\n")) text = text.substring(0, text.length() - 2);
    if (text.length() == 1 && text.charAt(0) == '^')  return;

    int i = text.indexOf(ourErrorMarker);
    if (i > 0) {
      String errorPlace = text.substring(0, i);
      int j = errorPlace.lastIndexOf(':');
      if (j > 0) {
        String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL,
            errorPlace.substring(0, j).replace(File.separatorChar, '/'));
        
        try {
          Integer lineNumber = Integer.valueOf(errorPlace.substring(j + 1, errorPlace.length()));
          String message = text.substring(i + 1).trim();
          myContext.addMessage(CompilerMessageCategory.ERROR, message, url, lineNumber, 1);
          mySkipNextLine = true;
          return;
        } catch (NumberFormatException e) {
          myContext.addMessage(CompilerMessageCategory.INFORMATION, "", text, -1, -1);
          return;
        }
      }
    }
    myContext.addMessage(CompilerMessageCategory.INFORMATION, text, null, -1, -1);
  }
}
