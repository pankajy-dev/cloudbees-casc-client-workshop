package com.cloudbees.jenkins.plugins.casc;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import org.apache.commons.lang.math.NumberUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.BaseConstructor;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

public class YamlClientUtils {

    public static String getPropertyOrEnv(String envKey, String proKey) {
        return Util.fixEmptyAndTrim(System.getProperty(proKey, System.getenv(envKey)));
    }
    static public Yaml createDefault() {
        LoaderOptions loaderOptions = new LoaderOptions();
        String prop = getPropertyOrEnv("CASC_YAML_MAX_ALIASES", "casc.yaml.max.aliases");
        loaderOptions.setMaxAliasesForCollections(NumberUtils.toInt(prop, 50));
        return new Builder().setLoaderOptions(loaderOptions).build();
    }

    @SuppressFBWarnings
    public static class Builder {
        private LoaderOptions loaderOptions = new LoaderOptions();
        private DumperOptions dumperOptions = new DumperOptions();
        private BaseConstructor constructor = new SafeConstructor(new LoaderOptions());
        private Representer representer = new Representer(new DumperOptions());

        public Builder setLoaderOptions(LoaderOptions loaderOptions) {
            this.loaderOptions = loaderOptions;
            return this;
        }

        public Builder setDumperOptions(DumperOptions dumperOptions) {
            this.dumperOptions = dumperOptions;
            return this;
        }

        public Builder setBaseConstructor(BaseConstructor constructor) {
            this.constructor = constructor;
            return this;
        }

        public Builder setRepresenter(Representer representer) {
            this.representer = representer;
            return this;
        }

        public Builder() {
        }

        public static Builder create(){
            return new Builder();
        }
        public Builder(LoaderOptions loaderOptions, DumperOptions dumperOptions, Constructor constructor, Representer representer) {
            this.loaderOptions = loaderOptions;
            this.dumperOptions = dumperOptions;
            this.constructor = constructor;
            this.representer = representer;
        }

        public Yaml build(){
            return new Yaml(constructor, representer, dumperOptions, loaderOptions);
        }
    }
}
