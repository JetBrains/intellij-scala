package scala.meta.intellij;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * @author mutcianm
 * @since 26.03.17.
 */
public class MetaAnnotationRunner {
    public static InputStream run(Class<?> clazz, int argc, InputStream in) throws Exception {
        ObjectInputStream stream = new ObjectInputStream(in);
        Object[] args = new Object[argc];
        for (int i = 0; i < args.length; i++) {
            args[i] = stream.readObject();
        }
        Constructor<?> ctor = clazz.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object instance = ctor.newInstance();
        Method method = null;
        Method[] declaredMethods = clazz.getDeclaredMethods();
        for (int i = 0; i < declaredMethods.length; i++) {
            Method m = declaredMethods[i];
            if (m.getName().equals("apply")) method = m;
        }
        assert method != null: "Method 'apply' not found in annotation class";
        method.setAccessible(true);
        Object result = method.invoke(instance, args);
        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream(2048);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(arrayOutputStream);
        try {
            objectOutputStream.writeObject(result);
            return new ObjectInputStream(new ByteArrayInputStream(arrayOutputStream.toByteArray()));
        } finally {
            objectOutputStream.close();
        }
    }
}
