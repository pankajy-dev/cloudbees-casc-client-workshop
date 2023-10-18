package com.cloudbees.opscenter.client.casc;

import java.io.IOException;
import javax.annotation.CheckForNull;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.POST;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.csrf.CrumbExclusion;

/**
 * This extension handles all requests that need to access unauthorized endpoints.
 * This doesn't mean there's no security check, this is intended to be used with authentication tokens for authentication
 * but for secured environments endpoints without authentication are not reachable.
 * Initially this class will hold web methods used by the casc-retriever
 */
@Extension
public class BundleReloadUnprotectedAction implements UnprotectedRootAction {

    private final static String CONTEXT = "casc-internal";

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return CONTEXT;
    }

    /**
     * This endpoint is analog to casc-bundle-mgmt one but will look for authentication token in request
     * headers.
     * Request params are the same as the original method but setting <code>quiet</code> to system default.
     * @{link BundleReloadAction#doGetBundleNewerVersion}
     * @param request the request
     * @return 200 if everything went ok
     *         403 on unauthorized
     */
    @GET
    @WebMethod(name = "check-bundle-update")
    public HttpResponse doGetBundleNewerVersionWithAuthToken(StaplerRequest request) {
        BundleReloadAction action = ExtensionList.lookupSingleton(BundleReloadAction.class);
        boolean accepted = InternalEndpointAuthentication.get().validate(request);
        if (accepted) {
            try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                return action.doGetBundleNewerVersion(null);
            }
        } else { // Same operation but without permissions, to keep original failure
            return action.doGetBundleNewerVersion(null);
        }
    }

    /**
     * This endpoint is analog to casc-bundle-mgmt one but will look for authentication token in request
     * headers.
     * Request params are the same as the original method but setting <code>quiet</code> to system default.
     * {@link BundleReloadAction#doBundleValidate}
     * @param req the request
     * @param commit the commit's SHA
     * @return 200 if everything went ok
     *         403 on unauthorized
     */
    @POST
    @WebMethod(name = "casc-bundle-validate")
    public HttpResponse doBundleValidateWithAuthToken(StaplerRequest req,
                                                      @QueryParameter String commit) {
        BundleReloadAction action = ExtensionList.lookupSingleton(BundleReloadAction.class);
        boolean accepted = InternalEndpointAuthentication.get().validate(req);
        if (accepted) {
            try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                return action.doBundleValidate(req, commit, null);
            }
        } else { // Same operation but without permissions, to keep original failure
            return action.doBundleValidate(req, commit, null);
        }
    }

    /**
     * Needed extension to handle POST requests, as regardles being Unprotected POST requests will still
     * need the crumb.
     * This doesn't mean endpoints is unprotected, as it uses authentication token to check legitimate requests
     */
    @Extension
    public static class BundleValidationCrumbExclusion extends CrumbExclusion {
        @Override
        public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
                throws IOException, ServletException {
            String pathInfo = req.getPathInfo();
            if (pathInfo != null && pathInfo.startsWith('/' + CONTEXT + "/casc-bundle-validate/")) {
                chain.doFilter(req, resp);
                return true;
            }
            return false;
        }
    }
}
