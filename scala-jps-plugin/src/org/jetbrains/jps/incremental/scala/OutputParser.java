package org.jetbrains.jps.incremental.scala;

import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.MessageHandler;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ilyas, Pavel Fatin
 */

class OutputParser extends MessageProducer {
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
  private StringBuilder myErrorText = new StringBuilder();
  private boolean stopProcessing = false;
  private boolean notUrlErrorProcessing = false;
  private int myMsgColumnMarker;
  private MESSAGE_TYPE myMsgType = MESSAGE_TYPE.PLAIN;
  private ArrayList<String> myWrittenList = new ArrayList<String>();
  private static final Pattern SCALA_29_PATTERN = Pattern.compile("'.*' to (.+)");

  static enum MESSAGE_TYPE {
    ERROR, WARNING, PLAIN
  }

  private Integer myLineNumber;
  private String myMessage;
  private String myUrl;
  private boolean myIsFirstLine = true;

  private MessageHandler myHandler;

  OutputParser(MessageHandler messageHandler, String compilerName) {
    super(messageHandler, compilerName);
  }

  public List<String> getGeneratedFiles() {
    return myWrittenList;
  }

  public void finishProcessing() {
    if (myErrorText.length() > 0) {
      error(myErrorText.toString());
    }
  }

  public boolean processMessageLine(String line) {
    if(myIsFirstLine && ExceptionMarkerPattern.matcher(line).find()) {
      fullCrash = true;
      myErrorText.append(line);
      return true;
    }

    myIsFirstLine = false;

    String text = line.trim();
    if (fullCrash && text.length() > 0) {
      myErrorText.append(text);
      return true;
    }

    if (text.endsWith("\r\n")) text = text.substring(0, text.length() - 2);
    if (text.startsWith(ourScalaInternalErrorMsg)) {
      fullCrash = true;
      myErrorText.append(text);
      return true;
    }

    //this is for cases like: UTF-8 error
    if (text.startsWith(errorMarker)) {
      error(text.substring(errorMarker.length()));
      notUrlErrorProcessing = true;
      return true;
    }

    if (notUrlErrorProcessing && !stopMsgProcessing(text) && !stopProcessing) {
      error(text);
      return true;
    } else if (notUrlErrorProcessing) {
      notUrlErrorProcessing = false;
    }


    // Add error message to output
    if (myMessage != null && stopMsgProcessing(text) && (mustProcessMsg || stopProcessing)) {
      myMsgColumnMarker = myMsgColumnMarker > 0 ? myMsgColumnMarker : 1;
      if (myMsgType == MESSAGE_TYPE.ERROR) {
        error(myMessage, myUrl, myLineNumber, myMsgColumnMarker);
      } else if (myMsgType == MESSAGE_TYPE.WARNING) {
        warn(myMessage, myUrl, myLineNumber, myMsgColumnMarker);
      }
      myMessage = null;
      myMsgType = MESSAGE_TYPE.PLAIN;
      mustProcessMsg = false;
      stopProcessing = false;
    }

    if (text.indexOf(ourErrorMarker) > 0) { // new error occurred
      processErrorMessage(text, text.indexOf(ourErrorMarker), MESSAGE_TYPE.ERROR);
    } else if (text.indexOf(ourWarningMarker) > 0) {
      processErrorMessage(text, text.indexOf(ourWarningMarker), MESSAGE_TYPE.WARNING);
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
          progress(info);
          // Set file processed
//          callback.fileProcessed(info.substring(info.indexOf(ourParsingMarker) + ourParsingMarker.length()));
          // TODO
        }
        //add phases and their times to output
        else if (getPhaseName(info) != null) {
          progress("Phase " + getPhaseName(info) + " passed" + info.substring(getPhaseName(info).length()));
        } else if (info.startsWith("loaded")) {
          if (info.startsWith("loaded class file ")) { // Loaded file
            final int end = info.indexOf(".class") + ".class".length();
            final int begin = info.substring(0, end - 1).lastIndexOf("/") + 1;
            progress("Loaded file " + info.substring(begin, end));
          } else if (info.startsWith("loaded directory path ")) {
            final int end = info.indexOf(".jar") + ".jar".length();
            final int begin = info.substring(0, end - 1).lastIndexOf("/") + 1;
            progress("Loaded directory path " + info.substring(begin, end));
          }
//          callback.setProgressText("Loading files...");
        } else if (info.startsWith(ourWroteBeanInfoMarker)) {
          checkOutput(info, ourWroteBeanInfoMarker);
        } else if (info.startsWith(ourWroteMarker)) {
          checkOutput(info, ourWroteMarker);
        }
      }
    }
    return true;
  }

  private void checkOutput(String info, final String ourWroteBeanInfoMarker) {
    progress(info);
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
        error(message);
        return;
      }
    }

    myWrittenList.add(path);
  }

  private static String getPhaseName(@NotNull String st) {
    for (String phase : PHASES) {
      if (st.startsWith(phase + " ")) return phase;
    }
    return null;
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
  private void processErrorMessage(String text, int errIndex, MESSAGE_TYPE msgType) {
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
        info(text);
        myMessage = null;
        mustProcessMsg = false;
        myMsgType = MESSAGE_TYPE.PLAIN;
      }
    } else {
      info(text, text);
      myMsgType = MESSAGE_TYPE.PLAIN;
    }
  }
}
