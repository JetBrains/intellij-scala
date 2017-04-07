package org.jetbrains.plugins.scala.settings;

import com.intellij.codeInsight.problems.WolfTheProblemSolverImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * @author Pavel Fatin
 */
class ProblemSolverUtils {
  // TODO Extend IDEA API by adding clearProblems() to the WolfTheProblemSolver interface
  static void clearProblemsIn(Project project) {
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(project);

    try {
      Field problemsField = WolfTheProblemSolverImpl.class.getDeclaredField("myProblems");
      problemsField.setAccessible(true);
      //noinspection unchecked
      Map<VirtualFile, Object> problems = ((Map<VirtualFile, Object>) problemsField.get(wolf));

      VirtualFile[] files;
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (problems) {
        files = VfsUtilCore.toVirtualFileArray(problems.keySet());
      }

      Method doRemoveMethod = WolfTheProblemSolverImpl.class.getDeclaredMethod("doRemove", VirtualFile.class);
      doRemoveMethod.setAccessible(true);

      for (VirtualFile file : files) {
        doRemoveMethod.invoke(wolf, file);
      }
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }
}
