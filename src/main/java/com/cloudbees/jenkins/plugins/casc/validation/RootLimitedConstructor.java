package com.cloudbees.jenkins.plugins.casc.validation;

import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RootLimitedConstructor extends Constructor {

    private static final Logger LOGGER = Logger.getLogger(RootLimitedConstructor.class.getName());

    private Class<? extends Object> theRoot;
    public RootLimitedConstructor(Class<? extends Object> theRoot) {
        super(theRoot, new LoaderOptions());
        this.theRoot = theRoot;
    }
    @Override
    protected Class<?> getClassForName(String name) throws ClassNotFoundException {
        if (!this.theRoot.getName().equals(name)) {
            LOGGER.log(Level.WARNING, "Blocking class deserialization for security reasons: [{0}]", name);
            throw new ClassNotFoundException("refusing to unmarshal " + name);
        }
        return super.getClassForName(name);
    }
}