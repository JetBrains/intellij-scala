package org.jetbrains.plugins.scala.lang.stubs

import com.intellij.psi.stubs.{PsiFileStub, SerializationManagerEx, StubTree}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

private[stubs] object StubSerializationTestUtil {
  /**
   * Reconstructs a file's stub tree through the platform's binary stub serializer.
   * This exercises stub externalizers without reusing the original stubs or rebuilding
   * them from source PSI. The returned tree is not attached to the original PSI file.
   *
   * @param tree the existing file-stub tree to serialize
   * @return a new tree deserialized from the serialized bytes held in memory
   */
  def roundTripStubTree(tree: StubTree): StubTree = {
    val bytes = new ByteArrayOutputStream
    val serializer = SerializationManagerEx.getInstanceEx
    serializer.serialize(tree.getRoot, bytes)
    val restored = serializer.deserialize(new ByteArrayInputStream(bytes.toByteArray))
    new StubTree(restored.asInstanceOf[PsiFileStub[?]])
  }
}
