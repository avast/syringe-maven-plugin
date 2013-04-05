package com.avast.syringe.mvn;

import com.avast.syringe.config.perspective.ModuleGenerator;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Properties;

/**
 * Goal which generates Scala module for the given config class.
 *
 * @goal generate-module
 * @phase process-classes
 */
public class ScalaModuleGeneratingMojo
        extends ModuleScannerMojo {

    /**
     * Location of the generated files.
     *
     * @parameter expression="src/main/scala"
     * @required
     */
    private File generatedClassesDirectory;

    /**
     * Syringe module name
     *
     * @parameter expression="${project.name}"
     * @required
     */
    private String moduleName;

    /**
     * The description of the module
     *
     * @parameter expression="${project.description}"
     */
    private String moduleDesc;

    /**
     * Package of the generated module (Scala object)
     *
     * @parameter expression="${project.groupId}"
     */
    private String modulePackage;

    /**
     * A list of module traits. Each module trait is specified as a fully-qualified name of the trait.
     *
     * @parameter
     */
    private List moduleTraits;

    /**
     * Builder traits mappings. Each property key specifies a pattern for component classes and the value specifies
     * a fully-qualified name of the extending builder trait. Every component builder that builds the matching component
     * class will be extended with the builder trait.
     *
     * @parameter
     */
    private Properties builderTraits;

    @Override
    protected void processClasses(List<Class> injectableClasses) throws Exception {
        getLog().info("Syringe Perspectives Generator");

        String relPath = modulePackage.replace('.', File.separatorChar);
        File parentDir = new File(generatedClassesDirectory, relPath);
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        File targetFile = new File(parentDir, moduleName + ".scala");
        FileWriter writer = new FileWriter(targetFile);
        try {

            for (Class injectableClass : injectableClasses) {
                getLog().info("Generating builder for " + injectableClass.getName() + " for " + moduleName);
            }

            ModuleGenerator.getInstance().generate(modulePackage, moduleDesc, moduleName, injectableClasses,
                    moduleTraits, builderTraits, writer);
        } finally {
            writer.close();
        }
    }
}
