package org.jetbrains.jps.incremental.scala.local.worksheet.compatibility;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Naydanov
 * Date: 13.03.17.
 */
public class WorksheetArgsJava {
  private final String worksheetClassName;
  private final File pathToRunners;
  private final File worksheetTempFile;
  private final List<File> outputDirs;
  private final String nameForST;
  private final List<URL> classpathURLs;

  private final File compiler;
  private final File compLibrary;
  private final List<File> compExtra; 
  
  private final ReplArgsJava replArgs;

   private WorksheetArgsJava(String worksheetClassName, File pathToRunners, File worksheetTempFile, List<File> outputDirs,
                             String nameForST, List<URL> classpathURLs, File compLibrary, File compiler,
                             List<File> compExtra, ReplArgsJava replArgs) {
    this.worksheetClassName = worksheetClassName;
    this.pathToRunners = pathToRunners;
    this.worksheetTempFile = worksheetTempFile;
    this.outputDirs = outputDirs;
    this.nameForST = nameForST;
    this.classpathURLs = classpathURLs;
    this.compiler = compiler;
    this.compExtra = compExtra;
    this.compLibrary = compLibrary;
    this.replArgs = replArgs;
  }


  public static WorksheetArgsJava constructArgsFrom(List<String> argsString, String nameForST, File compLibrary,
                                                    File compiler, List<File> compExtra, List<File> classpath) {
    if (argsString.isEmpty()) return null;
    int argsSize = argsString.size();
    
    String last = argsString.get(argsSize - 1);
    ReplArgsJava replArgs = "replenabled".equals(last) ? new ReplArgsJava(argsString.get(argsSize - 3), argsString.get(argsSize - 2)) : null;
    
    String worksheetClassName = argsString.get(0);
    File pathToRunners = validate(pathToFile(argsString.get(1)), "pathToRunners");
    File worksheetTemp = validate(pathToFile(argsString.get(2)), "worksheetTempFile");

    ArrayList<File> outputDirs = new ArrayList<File>(argsSize);
    for (int i = 3; i < argsSize - 3; ++i) {
      File f = pathToFile(argsString.get(i));
      if (f != null) outputDirs.add(f);
    }
    
    ArrayList<URL> classPathUrls = new ArrayList<URL>(classpath.size());
    for (File aClasspath : classpath)
      try {
        classPathUrls.add(aClasspath.toURI().toURL());
      } catch (MalformedURLException e) {
        //ignore
      }
    
      
    return new WorksheetArgsJava(worksheetClassName, pathToRunners, worksheetTemp, outputDirs, nameForST, classPathUrls, 
        compLibrary, compiler, compExtra, replArgs);  
  }

  private static File pathToFile(String path) {
    if (path == null) return null;
    File f = new File(path);
    
    return f.exists()? f : null;
  }
  
  private static File validate(File file, String argName) {
    if (file == null) error("File " + argName + " is null");
    if (!file.exists()) error("File " + argName + " with path " + file.getAbsolutePath() + " doesn't exist");
    
    return file;
  }
  
  private static void error(String msg) {
    throw new IllegalArgumentException(msg);
  }
  
  public File getWorksheetTempFile() {
    return worksheetTempFile;
  }

  public File getPathToRunners() {
    return pathToRunners;
  }

  public String getWorksheetClassName() {
    return worksheetClassName;
  }

  public List<File> getOutputDirs() {
    return outputDirs;
  }

  public String getNameForST() {
    return nameForST;
  }

  public List<URL> getClasspathURLs() {
    return classpathURLs;
  }

  public File getCompiler() {
    return compiler;
  }

  public List<File> getCompExtra() {
    return compExtra;
  }

  public File getCompLibrary() {
    return compLibrary;
  }

  public ReplArgsJava getReplArgs() {
    return replArgs;
  }
}
