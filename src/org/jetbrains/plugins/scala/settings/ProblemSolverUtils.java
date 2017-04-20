package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Pavel Fatin
 */
class ProblemSolverUtils {
  // TODO Extend IDEA API by adding clearProblems() to the WolfTheProblemSolver interface
  static void clearProblemsIn(Project project) {
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(project);

    ValueAccumulator<VirtualFile> accumulator = new ValueAccumulator<VirtualFile>();
    // Using Condition only for the side-effect
    wolf.hasProblemFilesBeneath(accumulator);

    for (VirtualFile file : accumulator.getValues()) {
      wolf.clearProblems(file);
    }
  }

  private static class ValueAccumulator<T> implements Condition<T> {
    private final List<T> myValues = new ArrayList<T>();

    @Override
    public boolean value(T value) {
      myValues.add(value);
      return false;
    }

    List<T> getValues() {
      return myValues;
    }
  }
}
