package scala.meta.intellij;

import scala.collection.immutable.Map;
import scala.meta.Dialect;
import scala.meta.Dialect$;
import scala.meta.Stat;
import scala.meta.Tree;
import scala.meta.inputs.Input$;
import scala.meta.parsers.Parse$;
import scala.meta.parsers.Parsed;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
        Object result = invokeAnnotation(clazz, args);
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

    public static String runString(Class<?> clazz, String[] args) throws Exception {
        Map<String, Dialect> standards = Dialect$.MODULE$.standards();
        Object[] trees = new Tree[args.length];
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            Parsed<Stat> parsed = Parse$.MODULE$.parseStat().apply(Input$.MODULE$.stringToInput().apply(arg), standards.get("Scala212").get());
            trees[i] = parsed.get();
        }
        Object result = invokeAnnotation(clazz, trees);
        return result.toString();
    }


    // TODO: set working dir
    private static Object invokeAnnotation(Class<?> clazz, Object[] trees) throws InstantiationException, IllegalAccessException, InvocationTargetException {
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
        Object result;
        try {
            result = method.invoke(instance, trees);
        }   catch (InvocationTargetException e) {
            // we can't even pass exceptions without re-wraping them since classes on the invoking side are incompatible
            // also flatten to avoid getting nested ITEs
            throw new RuntimeException(e.getTargetException().toString());
        }
        return result;
    }
}
