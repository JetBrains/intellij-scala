package org.jetbrains.plugins.scala.compiler;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.javaCompiler.FileObject;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import static org.jetbrains.plugins.scala.compiler.ScalacOutputParser.MESSAGE_TYPE.*;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ilyas
 */
class ScalacOutputParser extends OutputParser {
  public static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.scala.compiler.ScalacOutputParser");

  @NonNls
  private static final Pattern ExceptionMarkerPattern =
      Pattern.compile("(?-i)(\\p{Ll}+\\.)+(\\p{Lu}\\p{Ll}+)+(Exception|Error)");

  @NonNls
  private static final String errorMarker = "error:";
  @NonNls
  private static final String ourErrorMarker = " " + errorMarker;
  @NonNls
  private static final String ourWarningMarker = " warning:";
  @NonNls
  private static final String ourInfoMarkerStart = "[";
  @NonNls
  private static final String ourInfoMarkerEnd = "]";
  @NonNls
  private static final String ourWroteMarker = "wrote ";
  @NonNls
  private static final String ourWroteBeanInfoMarker = "wrote BeanInfo ";
  @NonNls
  private static final String ourColumnMarker = "^";
  @NonNls
  private static final String ourParsingMarker = "parsing ";
  @NonNls
  private static final String ourScalaInternalErrorMsg = "Scalac internal error";

  // Phases
  @NonNls
  private static final String PHASE = "running phase ";
  @NonNls
  private static final String PARSER_ON = "parser on ";

  @NonNls
  private static Set<String> PHASES = new HashSet<String>();

  static {
    PHASES.addAll(Arrays.asList(
        "parser", "namer", "typer", "superaccessors", "pickler", "refchecks", "liftcode",
        "uncurry", "tailcalls", "explicitouter", "cleanup", "total", "inliner", "jvm",
        "closelim", "inliner", "dce", "mixin", "flatten", "constructors", "erasure", "lazyvals",
        "lambdalift"
    ));
  }

  private boolean mustProcessMsg = false;
  private boolean fullCrash = false;
  private boolean stopProcessing = false;
  private boolean notUrlErrorProcessing = false;
  private int myMsgColumnMarker;
  private MESSAGE_TYPE myMsgType = PLAIN;
  private ArrayList<String> myWrittenList = new ArrayList<String>();
  private final Object WRITTEN_LIST_LOCK = new Object();
  private static final Pattern SCALA_29_PATTERN = Pattern.compile("'.*' to (.+)");

  static enum MESSAGE_TYPE {
    ERROR, WARNING, PLAIN
  }

  private Integer myLineNumber;
  private String myMessage;
  private String myUrl;
  private boolean myIsFirstLine = true;


  @Override
  public boolean processMessageLine(Callback callback) {
    final String line = callback.getNextLine();

    if (LOG.isDebugEnabled()) {
      LOG.debug(line);
    }

    if (line == null) {
      flushWrittenList(callback);
      return false;
    }

    if(myIsFirstLine && ExceptionMarkerPattern.matcher(line).find()) {
      String text = appendLines(new StringBuilder(line), callback);
      callback.message(CompilerMessageCategory.ERROR, text, "", 0, 0);
      return true;
    }

    myIsFirstLine = false;

    String text = line.trim();
    if (fullCrash && text.length( ) > 0) {
      callback.message(CompilerMessageCategory.ERROR, text, "", 0, 0);
      return true;
    }

    if (text.endsWith("\r\n")) text = text.substring(0, text.length() - 2);
    if (text.startsWith(ourScalaInternalErrorMsg)) {
      callback.message(CompilerMessageCategory.ERROR, text, "", 0, 0);
      fullCrash = true;
      return true;
    }

    //this is for cases like: UTF-8 error
    if (text.startsWith(errorMarker)) {
      callback.message(CompilerMessageCategory.ERROR, text.substring(errorMarker.length()), "", -1, -1);
      notUrlErrorProcessing = true;
      return true;
    }

    if (notUrlErrorProcessing && !stopMsgProcessing(text) && !stopProcessing) {
      callback.message(CompilerMessageCategory.ERROR, text, "", -1, -1);
      return true;
    } else if (notUrlErrorProcessing) {
      notUrlErrorProcessing = false;
    }


    // Add error message to output
    if (myMessage != null && stopMsgProcessing(text) && (mustProcessMsg || stopProcessing)) {
      myMsgColumnMarker = myMsgColumnMarker > 0 ? myMsgColumnMarker : 1;
      if (myMsgType == ERROR) {
        callback.message(CompilerMessageCategory.ERROR, myMessage, myUrl, myLineNumber, myMsgColumnMarker);
      } else if (myMsgType == WARNING) {
        callback.message(CompilerMessageCategory.WARNING, myMessage, myUrl, myLineNumber, myMsgColumnMarker);
      }
      myMessage = null;
      myMsgType = PLAIN;
      mustProcessMsg = false;
      stopProcessing = false;
    }

    if (text.indexOf(ourErrorMarker) > 0) { // new error occurred
      processErrorMessage(text, text.indexOf(ourErrorMarker), ERROR, callback);
    } else if (text.indexOf(ourWarningMarker) > 0) {
      processErrorMessage(text, text.indexOf(ourWarningMarker), WARNING, callback);
    } else if (!text.startsWith(ourInfoMarkerStart)) { //  continuing process [error | warning] message
      if (mustProcessMsg) {
        if (ourColumnMarker.equals(text.trim())) {
          myMsgColumnMarker = line.indexOf(ourColumnMarker) + 1;
          stopProcessing = true;
        } else if (myMessage != null) {
          myMessage += "\n" + text;
        } else {
          mustProcessMsg = false;
        }
      }
    } else { //verbose compiler output
      mustProcessMsg = false;
      if (text.endsWith(ourInfoMarkerEnd)) {
        String info = text.substring(ourInfoMarkerStart.length(), text.length() - ourInfoMarkerEnd.length());
        if (info.startsWith(ourParsingMarker)) { //parsing
          callback.setProgressText(info);
          // Set file processed
          callback.fileProcessed(info.substring(info.indexOf(ourParsingMarker) + ourParsingMarker.length()));
        }
        //add phases and their times to output
        else if (getPhaseName(info) != null) {
          callback.setProgressText("Phase " + getPhaseName(info) + " passed" + info.substring(getPhaseName(info).length()));
        } else if (info.startsWith("loaded")) {
          if (info.startsWith("loaded class file ")) { // Loaded file
            final int end = info.indexOf(".class") + ".class".length();
            final int begin = info.substring(0, end - 1).lastIndexOf("/") + 1;
            callback.setProgressText("Loaded file " + info.substring(begin, end));
          } else if (info.startsWith("loaded directory path ")) {
            final int end = info.indexOf(".jar") + ".jar".length();
            final int begin = info.substring(0, end - 1).lastIndexOf("/") + 1;
            callback.setProgressText("Loaded directory path " + info.substring(begin, end));
          }
//          callback.setProgressText("Loading files...");
        } else if (info.startsWith(ourWroteBeanInfoMarker)) {
          checkOutput(callback, info, ourWroteBeanInfoMarker);
        } else if (info.startsWith(ourWroteMarker)) {
          checkOutput(callback, info, ourWroteMarker);
        }
      }
    }
    return true;
  }

  private void checkOutput(Callback callback, String info, final String ourWroteBeanInfoMarker) {
    callback.setProgressText(info);
    String outputPath = info.substring(ourWroteBeanInfoMarker.length());
    String path = outputPath.replace(File.separatorChar, '/');
//          callback.fileGenerated(path);
    // See http://youtrack.jetbrains.net/issue/SCL-3175
    Matcher matcher = SCALA_29_PATTERN.matcher(path);
    if (matcher.matches()) {
      path = matcher.group(1);
    }

    if (path.contains("?")) {
      Charset sourceEncoding = EncodingManager.getInstance().getDefaultCharset();
      Charset jvmEncoding = CharsetToolkit.getDefaultSystemCharset();
      if (sourceEncoding != null && jvmEncoding != null && !sourceEncoding.equals(jvmEncoding)) {
        String message = String.format("Generated class file name contains incorrectly decoded symbols:\n%s\n" +
            "IDEA (and thus compiler) JVM encoding (%s) may be not compatibe with the encoding of source files (%s).\n" +
            "Please, check idea*.vmoptions file (add \"-Dfile.encoding=UTF-8\" line).",
            path, jvmEncoding, sourceEncoding);
        callback.message(CompilerMessageCategory.ERROR, message, "", -1, -1);
        return;
      }
    }

    synchronized (WRITTEN_LIST_LOCK) {
      myWrittenList.add(path);
    }
  }

  private static String getPhaseName(@NotNull String st) {
    for (String phase : PHASES) {
      if (st.startsWith(phase + " ")) return phase;
    }
    return null;
  }

  public void flushWrittenList(Callback callback) {
    synchronized (WRITTEN_LIST_LOCK) {
    //ensure that all "written" files are really written
      for (String s : myWrittenList) {
        File out = new File(s);
        callback.fileGenerated(new FileObject(out));
      }
      myWrittenList.clear();
    }
  }

  private boolean stopMsgProcessing(String text) {
    return text.startsWith(ourInfoMarkerStart) ||
            text.indexOf(ourErrorMarker) > 0 ||
            text.indexOf(ourWarningMarker) > 0 ||
            stopProcessing;
  }

  /*
 Collect information about error occurrence
  */
  private void processErrorMessage(String text, int errIndex, MESSAGE_TYPE msgType, Callback callback) {
    String errorPlace = text.substring(0, errIndex);
    if (errorPlace.endsWith(":"))
      errorPlace = errorPlace.substring(0, errorPlace.length() - 1); //hack over compiler output
    int j = errorPlace.lastIndexOf(':');
    if (j > 0) {
      myUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, errorPlace.substring(0, j).replace(File.separatorChar, '/'));
      try {
        myLineNumber = Integer.valueOf(errorPlace.substring(j + 1, errorPlace.length()));
        myMessage = text.substring(errIndex + 1).trim();
        mustProcessMsg = true;
        myMsgType = msgType;
      } catch (NumberFormatException e) {
        callback.message(CompilerMessageCategory.INFORMATION, "", text, -1, -1);
        myMessage = null;
        mustProcessMsg = false;
        myMsgType = PLAIN;
      }
    } else {
      callback.message(CompilerMessageCategory.INFORMATION, "", text, -1, -1);
      myMsgType = PLAIN;
    }
  }

  public boolean isTrimLines() {
    return false;
  }

  private String appendLines(StringBuilder builder, Callback callback) {
    String l = callback.getNextLine();
    while(l != null) {
      builder.append(l).append("\n");
      l = callback.getNextLine();
    }
    return builder.toString();
  }
}
