package com.avast.syringe.mvn;

import com.avast.syringe.config.XmlSchemaGenerator;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Goal which generates XSD files for all module config classes.
 *
 * @goal generate
 * @phase prepare-package
 */
public class XmlSchemaGeneratingMojo
        extends ModuleScannerMojo {

    /**
     * Location of the file.
     *
     * @parameter expression="src/main/resources"
     * @required
     */
    protected File resourcesDirectory;

    @Override
    protected void processClasses(List<Class> injectableClasses) throws MojoExecutionException, IOException {
        for (Class injectableClass : injectableClasses) {
            generateToFile(injectableClass);
        }
    }

    private void generateToFile(Class<?> injectableClass) throws MojoExecutionException, IOException {
        String schemaPath = injectableClass.getName().replace('.', '/') + ".xsd";
        File schemaFile = new File(resourcesDirectory, schemaPath);
        if (!schemaFile.getParentFile().exists()) {
            if (!schemaFile.getParentFile().mkdirs()) {
                throw new MojoExecutionException("Cannot create directories for " + schemaFile.getAbsolutePath());
            }
        }

        getLog().info("Generating schema for " + injectableClass.getName() + " into " + schemaFile.getAbsolutePath());

        FileOutputStream schemaOutputStream = new FileOutputStream(schemaFile);
        new XmlSchemaGenerator(injectableClass).generateXmlSchema(schemaOutputStream);
        schemaOutputStream.close();
    }
}
