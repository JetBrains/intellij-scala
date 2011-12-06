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

package org.jetbrains.plugins.scala.compiler.rt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class ClassRunner {
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new RuntimeException("Wrong arguments: " + Arrays.asList(args).toString());
    }

    File file = new File(args[1]);

    String[] lines;
    try {
      lines = readLinesFrom(file);
    } finally {
      if (file.delete()) {
        file.deleteOnExit();
      }
    }

    Class<?> scalacMain = Class.forName(args[0]);
    Method method = scalacMain.getMethod("main", String[].class);
    method.invoke(null, ((Object) lines));
  }

  private static String[] readLinesFrom(File file) throws IOException {
    List<String> lines = new LinkedList<String>();
    BufferedReader reader = new BufferedReader(new FileReader(file));

    try {
      String line;
      while (true) {
        line = reader.readLine();
        if (line == null) break;
        lines.add(line);
      }
    } finally {
      reader.close();
    }

    return lines.toArray(new String[lines.size()]);
  }
}
