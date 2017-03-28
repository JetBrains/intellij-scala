package scala.meta.intellij;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * @author mutcianm
 * @since 26.03.17.
 */
public class MetaAnnotationRunner {
    public static byte[] run(Class<?> clazz, int argc, byte[] data) throws Exception {
        Object[] args = new Object[argc];
        ObjectInputStream stream = null;
        try {
            stream = new ObjectInputStream(new ByteArrayInputStream(data));
            for (int i = 0; i < args.length; i++) {
                args[i] = stream.readObject();
            }
        } finally {
            if (stream != null) stream.close();
        }
        Constructor<?> ctor = clazz.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object instance = ctor.newInstance();
        Method method = null;
        Method[] declaredMethods = clazz.getDeclaredMethods();
        for (Method m : declaredMethods) {
            if (m.getName().equals("apply")) method = m;
        }
        assert method != null: "Method 'apply' not found in annotation class";
        method.setAccessible(true);
        Object result = method.invoke(instance, args);
        ByteArrayOutputStream arrayOutputStream;
        ObjectOutputStream objectOutputStream = null;
        try {
            arrayOutputStream = new ByteArrayOutputStream(2048);
            objectOutputStream = new ObjectOutputStream(arrayOutputStream);
            objectOutputStream.writeObject(result);
            return arrayOutputStream.toByteArray();
        } finally {
            if (objectOutputStream != null) objectOutputStream.close();
        }
    }
}
