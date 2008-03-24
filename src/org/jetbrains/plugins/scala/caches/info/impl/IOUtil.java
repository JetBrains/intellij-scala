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

package org.jetbrains.plugins.scala.caches.info.impl;

import com.intellij.util.containers.HashSet;
import org.jetbrains.plugins.scala.caches.info.ScalaDirectoryInfo;
import org.jetbrains.plugins.scala.caches.info.ScalaFileInfo;
import org.jetbrains.plugins.scala.caches.info.ScalaInfoBase;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;

/**
 * @author ven
 */
public class IOUtil {
  private static void writeSet(Set<String> set, DataOutputStream stream) throws IOException {
    stream.writeInt(set.size());
    for (String s : set) {
      stream.writeUTF(s);
    }
  }

  private static Set<String> readSet(DataInputStream stream) throws IOException {
    Set<String> result = new HashSet<String>();
    final int size = stream.readInt();
    for (int i = 0; i < size; i++) {
      result.add(stream.readUTF());
    }
    return result;
  }

  public static void writeNameInfo(NameInfo info, DataOutputStream stream) throws IOException {
    stream.writeUTF(info.getClassName());
    writeSet(info.getFileUrls(), stream);
  }

  public static NameInfo readNameInfo(DataInputStream stream) throws IOException {
    final String className = stream.readUTF();
    final Set<String> fileUrls = readSet(stream);
    return new NameInfo(className, fileUrls);
  }

  public static ScalaInfoBase readFileInfo(DataInputStream stream) throws IOException {
    final long fileStamp = stream.readLong();
    final String fileName = stream.readUTF();
    final String fileUrl = stream.readUTF();
    final boolean isDirectory = stream.readBoolean();
    if (isDirectory) {
      String[] children = readArray(stream);
      return ScalaInfoFactory.createDirectoryInfo(fileName, fileUrl, fileStamp, children);
    } else {
      String packageName = stream.readUTF();
      String[] classNames = readArray(stream);
      String[] extendedNames = readArray(stream);
      return ScalaInfoFactory.create(fileName, fileUrl, fileStamp, packageName, classNames, extendedNames);
    }
  }

  public static void writeFileInfo(ScalaInfoBase info, DataOutputStream stream) throws IOException {
    stream.writeLong(info.getFileTimestamp());
    stream.writeUTF(info.getFileName());
    stream.writeUTF(info.getFileUrl());
    stream.writeBoolean(info instanceof ScalaDirectoryInfo);
    if (info instanceof ScalaDirectoryInfo) {
      writeArray(stream, ((ScalaDirectoryInfo) info).getChildrenNames());
    } else {
      final ScalaFileInfo fileInfo = (ScalaFileInfo) info;
      stream.writeUTF(fileInfo.getPackageName());
      writeArray(stream, fileInfo.getClassNames());
      writeArray(stream, fileInfo.getExtendedNames());
    }
  }

  private static void writeArray(DataOutputStream stream, String[] strings) throws IOException {
    stream.writeInt(strings.length);
    for (String string : strings) {
      stream.writeUTF(string);
    }
  }

  private static String[] readArray(DataInputStream stream) throws IOException {
    final int childrenCount = stream.readInt();
    String[] children = new String[childrenCount];
    for (int i = 0; i < childrenCount; i++) {
      children[i] = stream.readUTF();
    }
    return children;
  }
}
