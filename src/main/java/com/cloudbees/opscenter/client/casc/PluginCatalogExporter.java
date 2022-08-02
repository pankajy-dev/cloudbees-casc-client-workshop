package com.cloudbees.opscenter.client.casc;

import com.cloudbees.jenkins.cjp.installmanager.CJPPluginManager;
import com.cloudbees.jenkins.plugins.assurance.CloudBeesAssurance;
import com.cloudbees.jenkins.plugins.assurance.model.Beekeeper;
import com.cloudbees.jenkins.plugins.assurance.model.PluginItem;
import com.cloudbees.jenkins.plugins.assurance.model.Plugins;
import com.cloudbees.jenkins.plugins.assurance.remote.BeekeeperRemote;
import com.cloudbees.jenkins.plugins.assurance.remote.Status;
import com.cloudbees.jenkins.plugins.assurance.remote.extensionparser.ParsedEnvelopeExtension;
import com.cloudbees.jenkins.plugins.assurance.remote.extensionparser.PluginConfiguration;
import com.cloudbees.jenkins.plugins.license.nectar.utils.ProductDescriptionUtils;
import com.cloudbees.jenkins.plugins.updates.envelope.EnvelopePlugin;
import com.cloudbees.jenkins.plugins.updates.envelope.EnvelopeProduct;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.PluginWrapper;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Exporter of the plugin-catalog.yaml file as text.
 */
@OptionalExtension(requirePlugins = "cloudbees-assurance", ordinal = 3)
public final class PluginCatalogExporter extends BundleExporter {

    private static final Logger LOG = Logger.getLogger(BundleExporter.class.getName());

    private String productId = null;
    private boolean isPluginCatalogEnabledInOC = Boolean.getBoolean(CJPPluginManager.class.getName() + ".enablePluginCatalogInOC");

    private String getProductId() {
        if (productId == null) {
            productId = ProductDescriptionUtils.getProductId();
        }

        return productId;
    }

    @Restricted(NoExternalUse.class)
    // Visible for testing
    public void setProductId(String productId) {
        this.productId = productId;
    }

    @Restricted(NoExternalUse.class)
    // Visible for testing
    public void setIsPluginCatalogEnabledInOC(boolean enabled) {
        this.isPluginCatalogEnabledInOC = enabled;
    }

    @Override
    public boolean enabled() {
            boolean isEnabled = true;
            final String productId = getProductId();
            final boolean isOC = EnvelopeProduct.CJOC.id().equals(productId) ||
                    EnvelopeProduct.CJE_OC.id().equals(productId) ||
                    EnvelopeProduct.CORE_OC.id().equals(productId) ||
                    EnvelopeProduct.CORE_OC_TRADITIONAL.id().equals(productId);
            if (isOC && !isPluginCatalogEnabledInOC) {
                isEnabled = false;
            }
        return isEnabled;
    }

    @NonNull
    @Override
    public String getYamlFile() {
        return "plugin-catalog.yaml";
    }

    /**
     * Generates a plugin catalog based on the current list of installed and active plugins.
     * <p>
     * If the instance already uses a catalog, it is taken as base. Otherwise it creates an new catalog.
     * Installed and active plugins which are not part of the envelope are added to the generated catalog. Note that
     * all those plugins are already compatible with the envelope, as we are assuming CAP is enabled and there is no
     * warnings on plugins.
     * <p>
     * If there is an installed and active plugin which is not in the envelope then that
     * plugin is recorded to be added in the plugin catalog generated.
     */
    @SuppressRestrictedWarnings(value = {CloudBeesAssurance.class, ParsedEnvelopeExtension.class, Beekeeper.class,
            Plugins.class, Plugins.PluginItemList.class, PluginItem.class})
    @Override
    @CheckForNull
    public String getExport() {
        if (!enabled()) {
            return null;
        }

        final List<PluginEntry> addToCatalog = addToCatalog();

        // check if the CAP is enabled
        Status status = BeekeeperRemote.get().getStatus();
        if (!status.isCap()) {
            String msg = "Cannot export catalog because CAP is not enabled";
            LOG.log(Level.WARNING, msg);
            return msg;
        }

        StringBuilder sb = new StringBuilder();
        if (CloudBeesAssurance.get().getBeekeeper().getPlugins().isThereAnyWarning()) {
            sb.append("--- There are Beekeeper warnings. This makes the bundle export a \"best effort\".\n");
            sb.append("--- Exported plugin catalog and plugins list might be incorrect and might need manual fixing before use.\n");
            sb.append(CloudBeesAssurance.get().getBeekeeper().getPlugins().getWarnings().stream()
                    .map(p -> "--- " + p.getName() + ". " + p.getDescription())
                    .collect(Collectors.joining("\n")));
            sb.append("\n");
            sb.append(CloudBeesAssurance.get().getBeekeeper().getPlugins().getWarningsExtension().stream()
                    .map(p -> "--- " + p.getName() + ". " + p.getDescription())
                    .collect(Collectors.joining("\n")));
            sb.append("\n");
        }
        // get current catalog
        String jsonCatalog = null;
        ParsedEnvelopeExtension.Expanded installedExtension = CloudBeesAssurance.get().getBeekeeper().getInstalledExtension();
        if (installedExtension != null) {
            jsonCatalog = installedExtension.getOriginalMetadata();
        } else if (!addToCatalog.isEmpty()) {
            jsonCatalog = catalogFromTemplate(getInstanceName());
        }
        // add plugins in addToCatalog if they are not there yet
        if (jsonCatalog != null) {
            JSONObject json = JSONObject.fromObject(jsonCatalog);
            JSONArray configurations = json.getJSONArray("configurations");
            JSONObject conf;
            if (configurations.size() >= 1) {
                // new plugins will be added to the first configuration, this can be manually edited by the user later
                // as a future improvement this could add the plugins to all configurations compatible with the current instance
                conf = (JSONObject) configurations.get(0);
            } else {
                conf = new JSONObject();
            }
            JSONObject includePlugins = conf.getJSONObject("includePlugins");

            addToCatalog.stream().sorted(new Comparator<PluginEntry>() {
                @Override
                public int compare(PluginEntry entry1, PluginEntry entry2) {
                    return entry1.id.compareTo(entry2.id);
                }
            }).forEachOrdered( entry -> {
                JSONObject version = new JSONObject();
                version.put("version", entry.version);
                includePlugins.put(entry.id, version);
            } );

            sb.append(toYaml(json));
            return sb.toString();
        } else {
            String msg = "There is nothing to add to the generated catalog";
            LOG.log(Level.FINE, msg);
            return "";
        }
    }

    @NonNull
    @Override
    public String getDescription() {
        return "The plugin catalog to install in the instance";
    }

    @Override
    public String getSection() {
        return "catalog";
    }

    /**
     * @return List of plugins that are installed and active and which are not in the envelope or in the active plugin catalog.
     */
    @SuppressRestrictedWarnings(value = {CloudBeesAssurance.class, Beekeeper.class})
    private List<PluginEntry> addToCatalog() {
        List<PluginEntry> toAdd = new ArrayList<>();

        List<PluginWrapper> installedPlugins = Jenkins.get().getPluginManager().getPlugins();
        // get plugins in the envelope and plugins in the plugin catalog
        Map<String, EnvelopePlugin> envelopePlugins = CloudBeesAssurance.get().getBeekeeper().getEnvelope().getPlugins();
        ParsedEnvelopeExtension.Expanded installedExtension = CloudBeesAssurance.get().getBeekeeper().getInstalledExtension();
        Map<String, PluginConfiguration.Expanded> extensionPlugins =
                installedExtension != null ? installedExtension.getConfiguration().getInclude() : Collections.emptyMap();

        Map<String, EnvelopePlugin> capPlugins = new HashMap<>();
        capPlugins.putAll(envelopePlugins);

        // envelope plugins and extended are the same
        for (PluginConfiguration.Expanded plugin : extensionPlugins.values()) {
            capPlugins.put(plugin.getPluginId(), plugin.asEnvelopePlugin());
        }

        for (PluginWrapper p : installedPlugins) {
            // if it is not in the envelope or in the plugin catalog, add it to the list
            if (!(p.isActive() && capPlugins.containsKey(p.getShortName()) && capPlugins.get(p.getShortName()).getVersionNumber().equals(p.getVersionNumber()))) {
                toAdd.add(new PluginEntry(p.getShortName(), p.getVersion()));
            }
        }

        return toAdd;
    }

    @CheckForNull
    private String catalogFromTemplate(@CheckForNull String masterName) {
        try (InputStream in = BundleExport.class.getResourceAsStream("plugin-catalog-template.json")){
            String template = IOUtils.toString(in, StandardCharsets.UTF_8);
            return template.replace("%MASTER_NAME%", masterName != null ? masterName : "Jenkins");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read plugin catalog template", e);
            return null;
        }
    }

    private String toYaml(JSONObject json) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(new SafeConstructor(), new Representer(), options);
        StringWriter writer = new StringWriter();
        yaml.dump(json, writer);
        return writer.toString();
    }

    static class PluginEntry {
        String id;
        String version;

        PluginEntry(String id, String version) {
            this.id = id;
            this.version = version;
        }
    }
}
