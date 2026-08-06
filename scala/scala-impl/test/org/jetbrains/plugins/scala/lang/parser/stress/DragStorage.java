package org.jetbrains.plugins.scala.lang.parser.stress;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;

import java.util.*;

public class DragStorage {

  private final Map<TextRange, Integer> myRanges = new HashMap<>();

  public void registerRevision(TextRange range) {
    if (myRanges.get(range) == null) {
      myRanges.put(range, 1);
    } else {
      Integer count = myRanges.get(range);
      myRanges.put(range, ++count);
    }
  }

  @SuppressWarnings("unchecked")
  public Pair<TextRange, Integer>[] getRangeInfo(){
    List<Pair<TextRange, Integer>> list = new ArrayList<Pair<TextRange, Integer>>();
    for (Map.Entry<TextRange, Integer> entry : myRanges.entrySet()) {
      list.add(new Pair<TextRange, Integer>(entry.getKey(), entry.getValue()));
    }
    Collections.sort(list, new RangeComparator());
    return list.toArray(new Pair[0]);
  }

  static class RangeComparator implements Comparator<Pair<TextRange, Integer>> {
    @Override
    public int compare(Pair<TextRange, Integer> pair1, Pair<TextRange, Integer> pair2) {
      TextRange range1 = pair1.getFirst();
      TextRange range2 = pair2.getFirst();
      return range1.getStartOffset() - range2.getEndOffset();
    }
  }
}
