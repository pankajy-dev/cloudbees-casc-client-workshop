package com.cloudbees.jenkins.plugins.casc;

import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import io.jenkins.plugins.casc.SecretSource;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

@OptionalExtension(requirePlugins = {"configuration-as-code"}, ordinal = 1000)
public class ConfigurationBundleSecretSource extends SecretSource {

    private final Map<String, String> variables = new HashMap<>();

    @Override
    public void init() {
        ConfigurationBundle bundle = ConfigurationBundleManager.get().getConfigurationBundle();
        variables.clear();
        if (bundle != null) {
            List<String> vars = bundle.getVariables();
            if (vars != null) {
                for (String plain : vars) {
                    variables.putAll(read(plain));
                }
            }
        }
    }

    private Map<String, String> read(String plain) {
        if (StringUtils.isBlank(plain)) {
            return Collections.emptyMap();
        }

        Map<String, String> variables = readFromYaml(plain);
        if (variables.isEmpty()) {
            variables = readFromProperties(plain);
        }

        return variables;
    }

    private Map<String, String> readFromProperties(String plain) {
        Properties vars = new Properties();
        try {
            vars.load(new StringReader(plain));
            Map<String, String> variables = new HashMap<>();
            for (Map.Entry entry : vars.entrySet()) {
                variables.put((String)entry.getKey(), (String)entry.getValue());
            }
            return variables;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Map<String, String> readFromYaml(String plain) {
        Map<String, Object> map = parseYaml(plain);

        List<Map<String,String>> vars = (List<Map<String,String>>)map.get("variables");
        if (vars != null) {
            Map<String,String> variables = new HashMap<>();
            for (Map<String, String> var : vars) {
                variables.putAll(var);
            }
            return variables;
        }

        return Collections.emptyMap();
    }


    private Map<String, Object> parseYaml(String content) {
        try {
            Yaml yaml = new Yaml(new SafeConstructor());
            return yaml.load(content);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    @Override
    public Optional<String> reveal(String secret) throws IOException {
        if (StringUtils.isBlank(secret)) {
            return Optional.empty();
        }
        return Optional.ofNullable(variables.get(secret)); // do we want to fallback to system property?
        //return Optional.ofNullable(System.getProperty(secret, System.getenv(secret)));
    }
}
