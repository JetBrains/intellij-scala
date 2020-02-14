package org.jetbrains.plugins.scala.worksheet;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** used in {@link org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerLocalEvaluator} */
public class PlainWorksheetRunner {
  private static final int TRACE_PREFIX = 13;
  
  public static void main(String[] args) throws IOException {
    String className = args[0];
    String fileName = args.length > 1? args[1] : null;
    
    try {
      Class<?> klass = ClassLoader.getSystemClassLoader().loadClass(className); //It's in default package, so name == fqn
      for (Method method : klass.getDeclaredMethods()) {
        if ("main".equals(method.getName()))
          method.invoke(null, java.lang.System.out);
      }
    } catch (InvocationTargetException e) {
      Throwable newThrowable = new StackTraceClean(e.getCause() == null? e : e.getCause(), fileName, className + "$" + className).clean();
      newThrowable.printStackTrace(System.out);
    } catch (Exception e) {
      e.printStackTrace(System.out);
    }
  }
  
  private static class StackTraceClean {
    private final static String WORKSHEET = "#worksheet#";
    
    Throwable e;
    String fileName; 
    String className;

    private StackTraceClean(Throwable e, String fileName, String className) {
      this.e = e;
      this.fileName = fileName;
      this.className = className;
    }

    private Throwable clean() {
      StackTraceElement[] els = e.getStackTrace();
      final int length = els.length;

      if (length < TRACE_PREFIX) return e;
      StackTraceElement[] newTrace = new StackTraceElement[length - TRACE_PREFIX + 1];
      StackTraceElement referenceElement = els[length - TRACE_PREFIX];

      newTrace[newTrace.length - 1] = new StackTraceElement(WORKSHEET, WORKSHEET,
          fileName == null? referenceElement.getFileName() : fileName,
          referenceElement.getLineNumber() - 4
      );

      for (int i = 0; i < newTrace.length - 1; ++i) {
        newTrace[i] = transformElement(els[i]);
      }
      
      e.setStackTrace(newTrace);

      return e;
    }
    
    private StackTraceElement transformElement(StackTraceElement original) {
      final String originalClassName = original.getClassName();
      final String declaringClassName = originalClassName.equals(className) ? WORKSHEET :
          (originalClassName.startsWith(className + "$")? WORKSHEET + "." + originalClassName.substring(className.length() + 1) : originalClassName);
      final String originalFileName = fileName == null ? original.getFileName() : fileName;

      return new StackTraceElement(declaringClassName, original.getMethodName(),
          originalFileName, original.getLineNumber() - 4);
    }
  }
}
