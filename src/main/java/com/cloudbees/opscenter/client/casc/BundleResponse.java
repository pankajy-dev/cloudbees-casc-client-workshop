package com.cloudbees.opscenter.client.casc;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Response object used by Core CasC export.
 * See {@link BundleExport}.
 */
@Restricted(NoExternalUse.class)
public class BundleResponse implements HttpResponse {

    /**
     * JCasC config file content.
     */
    private String jcasc;

    /**
     * Plugins list file content.
     */
    private String plugins;

    /**
     * Plugin catalog file content.
     */
    private String pluginCatalog;

    /**
     * Items file content.
     */
    private String items;

    /**
     * Bundle descriptor file content.
     */
    private String descriptor;

    /**
     * Global rbac groups and roles.
     */
    private String rbac;

    public BundleResponse(String descriptor, String jcasc, String plugins, String pluginCatalog, String items, String rbac) {
        this.jcasc = jcasc;
        this.plugins = plugins;
        this.pluginCatalog = pluginCatalog;
        this.descriptor = descriptor;
        this.rbac = rbac;
        this.items = items;
    }

    @Override
    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
        rsp.setContentType("text/plain; charset=UTF-8");
        StringBuilder b = new StringBuilder();
        b.append("---\n");
        b.append("# bundle.yaml\n");
        b.append(descriptor).append("\n");
        if (StringUtils.isNotBlank(jcasc)) {
            b.append("---\n");
            b.append("# jenkins.yaml\n");
            b.append(jcasc).append("\n");
        }
        if (StringUtils.isNotBlank(plugins)) {
            b.append("---\n");
            b.append("# plugins.yaml").append("\n");
            b.append(plugins).append("\n");
        }
        if (StringUtils.isNotBlank(pluginCatalog)) {
            b.append("---\n");
            b.append("# plugin-catalog.yaml").append("\n");
            b.append(pluginCatalog).append("\n");
        }
        if (StringUtils.isNotBlank(rbac)) {
            b.append("---\n");
            b.append("# rbac.yaml").append("\n");
            b.append(rbac).append("\n");
        }
        if (StringUtils.isNotBlank(items)) {
            b.append("---\n");
            b.append("# items.yaml").append("\n");
            b.append(items).append("\n");
        }
        String content = b.toString();
        rsp.setContentLength(content.getBytes(StandardCharsets.UTF_8).length);
        rsp.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
    }

    public String getJcasc() {
        return jcasc;
    }

    public String getPlugins() {
        return plugins;
    }

    public String getPluginCatalog() {
        return pluginCatalog;
    }

    public String getRbac() {
        return rbac;
    }

    public String getItems() {
        return items;
    }

    public String getDescriptor() {
        return descriptor;
    }

}
