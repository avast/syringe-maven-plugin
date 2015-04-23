package com.avast.syringe.mvn;

import com.avast.syringe.config.ConfigProperty;
import com.google.common.io.CharStreams;
import com.google.common.io.InputSupplier;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.ClassFile;
import javassist.bytecode.FieldInfo;
import javassist.bytecode.annotation.Annotation;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import org.reflections.scanners.AbstractScanner;
import org.reflections.util.ConfigurationBuilder;

import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * User: slajchrt
 * Date: 6/4/12
 * Time: 7:20 PM
 */
public abstract class ModuleScannerMojo extends AbstractMojo {

    /**
     * Location of built classes.
     *
     * @parameter expression="${project.build.directory}/classes"
     * @required
     */
    private File classesDirectory;

    /**
     * Location of the target file.
     *
     * @parameter expression="${project.build.directory}"
     * @required
     */
    private File outputDirectory;

    public void execute() throws MojoExecutionException {
        try {
            generateSchemas();
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            getLog().error(e);
        }
    }

    private void generateSchemas() throws Exception {
        File[] jars = collectJars();

        if (jars == null) {
            throw new MojoExecutionException("No classpath file found in the target directory");
        }

        List<URL> jarURLs = new ArrayList<URL>();
        jarURLs.add(classesDirectory.toURI().toURL());
        for (File jar : jars) {
            jarURLs.add(jar.toURI().toURL());
        }

        final List<String> injectableClassNames = new ArrayList<String>();
        // Scan classes only
        new Reflections(new ConfigurationBuilder().
            addUrls(classesDirectory.toURI().toURL()).setScanners(new AbstractScanner() {
            @Override
            public void scan(Object cls) {
                if ((cls instanceof ClassFile) && isInjectable((ClassFile) cls, getLog())) {
                    injectableClassNames.add(((ClassFile) cls).getName());
                }
            }
        }));

        URLClassLoader classLoader = new URLClassLoader(jarURLs.toArray(new URL[jarURLs.size()]), ConfigProperty.class.getClassLoader());
        ClassLoader[] loaders = new ClassLoader[]{classLoader};

        List<Class> injectableClasses = (List<Class>) (Object) ReflectionUtils.forNames(injectableClassNames, loaders);

        processClasses(injectableClasses);
    }

    protected abstract void processClasses(List<Class> injectableClasses) throws Exception;

    private <T extends Runnable & Closeable> T get(T t) {
        return t;
    }

    private File[] collectJars() throws IOException {
        File classpathFile = new File(outputDirectory, "classpath");
        if (!classpathFile.exists()) {
            return null;
        }

        final FileReader cpFileReader = new FileReader(classpathFile);
        String classpath = CharStreams.readFirstLine(new InputSupplier<FileReader>() {
            @Override
            public FileReader getInput() throws IOException {
                return cpFileReader;
            }
        });

        String[] jars = classpath.split(";");
        File[] jarFiles = new File[jars.length];
        for (int i = 0; i < jars.length; i++) {
            String jar = jars[i];
            jarFiles[i] = new File(jar);
        }

        return jarFiles;
    }

    static boolean isInjectable(ClassFile cf, Log log) {
        log.debug("Analyzing " + cf.getName());

        try {
            AttributeInfo classAttribute = cf.getAttribute(AnnotationsAttribute.visibleTag);
            if (classAttribute != null) {
                if (findAnnotation(cf, log, classAttribute, "com.avast.syringe.config.ConfigBean")) return true;
            }

            List<FieldInfo> fields = cf.getFields();
            for (FieldInfo field : fields) {
                AttributeInfo fieldAttribute = field.getAttribute(AnnotationsAttribute.visibleTag);
                if (fieldAttribute != null) {
                    if (findAnnotation(cf, log, fieldAttribute, "com.avast.syringe.config.ConfigProperty")) return true;
                }
            }

            log.debug(cf.getName() + " IS NOT an injection target");
        } catch (Throwable t) {
            log.error(t);
        }

        return false;
    }

    private static boolean findAnnotation(ClassFile cf, Log log, AttributeInfo classAttribute, String annotationName) {
        AnnotationsAttribute visible = new AnnotationsAttribute(classAttribute.getConstPool(),
            classAttribute.getName(),
            classAttribute.get());

        for (Annotation ann : visible.getAnnotations()) {
            if (annotationName.equals(ann.getTypeName())) {
                log.debug(cf.getName() + " IS an injection target");
                return true;
            }
        }
        return false;
    }

}
