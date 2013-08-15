package com.avast.syringe.mvn;

import com.avast.syringe.config.ConfigProperty;
import com.avast.syringe.config.XmlInstanceGenerator;
import javassist.bytecode.ClassFile;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import org.reflections.scanners.AbstractScanner;
import org.reflections.util.ConfigurationBuilder;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Goal which generates an XML file for the given config class.
 *
 * @goal create
 */
public class XmlConfigGeneratingMojo
        extends AbstractMojo {
    /**
     * Location of the file.
     *
     * @parameter expression="${project.build.directory}"
     * @required
     */
    private File outputDirectory;

    /**
     * Location of the config directory.
     *
     * @parameter default-value="src/config"
     * @required
     */
    private File configDirectory;

    /**
     * Location of the file.
     *
     * @parameter expression="${class}"
     */
    private String configClassName;

    /**
     * Config file name.
     *
     * @parameter expression="${name}"
     */
    private String configName;

    /**
     * Config file name.
     *
     * @parameter expression="${optional}"
     */
    private Boolean includeOptional;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {

            if (configClassName == null) {
                System.out.print("Config (simple) class name:");
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
                configClassName = bufferedReader.readLine();
            }

            if (configName == null) {
                System.out.print("Config file name (" + getShortName() + "):");
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
                configName = bufferedReader.readLine();

                if (configName == null || "".equals(configName)) {
                    configName = getShortName();
                }
            }

            if (includeOptional == null) {
                System.out.print("Include optional (false):");
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
                includeOptional = Boolean.parseBoolean(bufferedReader.readLine());
            }

            createConfigFile();
        } catch (MojoExecutionException e) {
            throw e;
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            getLog().error(e);
        }
    }

    private String getShortName() {
        int i = configClassName.lastIndexOf('.');
        return i < 0 ? configClassName : configClassName.substring(i + 1);
    }

    private void createConfigFile() throws Exception {
        File classesDir = new File(outputDirectory, "classes/");
        File libDir = new File(outputDirectory, "lib");
        File[] jars = libDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });
        if (jars == null) {
            throw new MojoExecutionException("No jar files found in " + libDir.getAbsolutePath());
        }

        List<URL> jarURLs = new ArrayList<URL>();
        jarURLs.add(classesDir.toURI().toURL());
        for (File jar : jars) {
            jarURLs.add(jar.toURI().toURL());
        }

        // Scan classes and all jars
        final List<String> candidates = new ArrayList<String>();
        new Reflections(new ConfigurationBuilder().
                addUrls(jarURLs).setScanners(new AbstractScanner() {
            @Override
            public void scan(Object cls) {
                if ((cls instanceof ClassFile)) {
                    ClassFile classFile = (ClassFile) cls;

                    if (XmlSchemaGeneratingMojo.isInjectable(classFile, getLog()) &&
                            (classFile.getName().equals(configClassName) || classFile.getName().endsWith(configClassName))) {
                        candidates.add(classFile.getName());
                    }
                }
            }
        }));

        if (candidates.isEmpty()) {
            throw new MojoFailureException("No class found for " + configClassName);
        }

        if (candidates.size() > 1) {
            throw new MojoFailureException("More classes found for " + configClassName + ":" + candidates);
        }

        String fullClassName = candidates.get(0);

        URLClassLoader classLoader = new URLClassLoader(jarURLs.toArray(new URL[jarURLs.size()]),
                ConfigProperty.class.getClassLoader());
        Class configClass = ReflectionUtils.forName(fullClassName, classLoader);
        generateToFile(configClass);
    }

    private void generateToFile(Class configClass) throws Exception {
        File configFile;
        String cfgName;
        if (configName == null) {
            cfgName = getShortName();
        } else {
            cfgName = configName;
        }

        if (!cfgName.endsWith(".xml")) {
            cfgName += ".xml";
        }

        configFile = new File(configDirectory, cfgName);

        if (!configFile.getParentFile().exists()) {
            if (!configFile.getParentFile().mkdirs()) {
                throw new MojoExecutionException("Cannot create directories for " + configFile.getAbsolutePath());
            }
        }


        FileOutputStream configOutputStream = new FileOutputStream(configFile);
        new XmlInstanceGenerator(configClass).generateXmlSchema(configOutputStream, !includeOptional);
        configOutputStream.close();

        getLog().info("Config file for " + configClass.getName() + " generated to " + configFile.getAbsolutePath());
    }

    public static void main(String[] args) throws Exception {
        XmlConfigGeneratingMojo xmlSchemaGeneratingMojo = new XmlConfigGeneratingMojo();
        xmlSchemaGeneratingMojo.outputDirectory = new File("target");
        xmlSchemaGeneratingMojo.configDirectory = new File("src/config");
        xmlSchemaGeneratingMojo.configClassName = "FilterX";
        xmlSchemaGeneratingMojo.configName = "FilterX-2";
        xmlSchemaGeneratingMojo.createConfigFile();
    }

}
