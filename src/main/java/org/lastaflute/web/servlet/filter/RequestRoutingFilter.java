/*
 * Copyright 2015-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.lastaflute.web.servlet.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dbflute.optional.OptionalThing;
import org.lastaflute.core.direction.FwAssistantDirector;
import org.lastaflute.core.util.ContainerUtil;
import org.lastaflute.web.path.ActionAdjustmentProvider;
import org.lastaflute.web.path.ActionFoundPathHandler;
import org.lastaflute.web.path.ActionPathResolver;
import org.lastaflute.web.response.HtmlResponse;
import org.lastaflute.web.ruts.ActionRequestProcessor;
import org.lastaflute.web.ruts.config.ActionExecute;
import org.lastaflute.web.ruts.process.urlparam.RequestUrlParam;
import org.lastaflute.web.ruts.process.urlparam.RequestUrlParamAnalyzer;
import org.lastaflute.web.servlet.request.RequestManager;
import org.lastaflute.web.util.LaActionExecuteUtil;
import org.lastaflute.web.util.LaModuleConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jflute
 */
public class RequestRoutingFilter implements Filter {

    // ===================================================================================
    //                                                                          Definition
    //                                                                          ==========
    private static final Logger logger = LoggerFactory.getLogger(RequestRoutingFilter.class);

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    // -----------------------------------------------------
    //                                                Cached
    //                                                ------
    /**
     * The cache of assistant director, which can be lazy-loaded when you get it.
     * Don't use these variables directly, you should use the getter. (NotNull: after lazy-load)
     */
    protected FwAssistantDirector cachedAssistantDirector;

    /**
     * The cache of request manager, which can be lazy-loaded when you get it.
     * Don't use these variables directly, you should use the getter. (NotNull: after lazy-load)
     */
    protected RequestManager cachedRequestManager;

    /**
     * The cache of action adjustment provider, which can be lazy-loaded when you get it.
     * Don't use these variables directly, you should use the getter. (NotNull: after lazy-load)
     */
    protected ActionAdjustmentProvider cachedActionAdjustmentProvider;

    /**
     * The cache of URL parameter analyzer, which can be lazy-loaded when you get it.
     * Don't use these variables directly, you should use the getter. (NotNull: after lazy-load)
     */
    protected RequestUrlParamAnalyzer cachedUrlParamAnalyzer;

    // -----------------------------------------------------
    //                                             Processor
    //                                             ---------
    /** The processor of action request, lazy loaded so use the getter. (NotNull: after lazy-load) */
    protected ActionRequestProcessor lazyLoadedProcessor; // lazy loaded

    // ===================================================================================
    //                                                                          Initialize
    //                                                                          ==========
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    // ===================================================================================
    //                                                                              Filter
    //                                                                              ======
    @Override
    public void doFilter(ServletRequest servReq, ServletResponse servRes, FilterChain chain) throws IOException, ServletException {
        final HttpServletRequest httpReq = (HttpServletRequest) servReq;
        final HttpServletResponse httpRes = (HttpServletResponse) servRes;
        final String requestPath = extractActionRequestPath(httpReq);
        if (!isRoutingTarget(httpReq, requestPath)) { // e.g. foo.jsp, foo.do, foo.js, foo.css
            chain.doFilter(httpReq, httpRes);
            return;
        }
        // no extension here (may be SAStruts URL)
        final ActionPathResolver resolver = getRequestManager().getActionPathResolver();
        try {
            final String contextPath = extractContextPath(httpReq);
            final ActionFoundPathHandler handler = createActionPathHandler(httpReq, httpRes, contextPath); // (#to_action)
            if (resolver.handleActionPath(requestPath, handler)) { // #to_action
                return;
            }
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            } else if (e instanceof ServletException) {
                throw (ServletException) e;
            } else if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else { // no way, just in case
                throw new IllegalStateException("*No way", e);
            }
        }
        // no routing here
        showExpectedRouting(requestPath, resolver);
        chain.doFilter(servReq, servRes);
    }

    protected String extractActionRequestPath(HttpServletRequest request) {
        // /= = = = = = = = = = = = = = = = = = = = = = = = = = = = = = 
        // request specification:
        //   requestURI  : /dockside/member/list/foo%2fbar/
        //   servletPath : /member/list/foo/bar/
        //
        // so uses requestURI but it needs to remove context path
        //  -> /member/list/foo%2fbar/
        // = = = = = = = = = =/
        return getRequestManager().getRequestPath();
    }

    protected String extractContextPath(HttpServletRequest req) {
        final String contextPath = req.getContextPath();
        return contextPath.equals("/") ? "" : contextPath;
    }

    protected boolean isRoutingTarget(HttpServletRequest request, String requestPath) {
        final ActionAdjustmentProvider adjustmentProvider = getActionAdjustmentProvider();
        if (adjustmentProvider.isForcedRoutingExcept(request, requestPath)) { // you can adjust it
            return false;
        }
        if (adjustmentProvider.isForcedRoutingTarget(request, requestPath)) { // you can adjust it
            return true;
        }
        return !isExtensionUrlPossible(request, requestPath); // default determination
    }

    protected boolean isExtensionUrlPossible(HttpServletRequest request, String requestPath) {
        // *added condition 'endsWith()' to allow /member/1.2.3/
        // (you can receive 'urlPattern' that contains dot '.')
        //
        // true  : e.g. foo.jsp, foo.do, foo.js, foo.css, /member/1.2.3
        // false : e.g. /member/list/, /member/list, /member/1.2.3/
        return requestPath.indexOf('.') >= 0 && !requestPath.endsWith("/");
    }

    protected ActionFoundPathHandler createActionPathHandler(HttpServletRequest httpReq, HttpServletResponse httpRes, String contextPath) {
        return (requestPath, actionName, paramPath, execByParam) -> {
            return routingToAction(httpReq, httpRes, contextPath, requestPath, actionName, paramPath, execByParam);
        };
    }

    protected void showExpectedRouting(String requestPath, ActionPathResolver resolver) { // for debug
        if (logger.isDebugEnabled()) {
            if (!requestPath.contains(".")) { // e.g. routing target can be adjusted so may be .jpg
                logger.debug(resolver.prepareExpectedRoutingMessage(requestPath));
            }
        }
    }

    // ===================================================================================
    //                                                                   Routing to Action
    //                                                                   =================
    protected boolean routingToAction(HttpServletRequest request, HttpServletResponse response, String contextPath, String requestPath,
            String actionName, String paramPath, ActionExecute execByParam) throws IOException, ServletException {
        if (execByParam != null) { // already found
            processAction(request, response, execByParam, paramPath); // #to_action
            return true;
        }
        final OptionalThing<ActionExecute> found = LaActionExecuteUtil.findActionExecute(actionName, request);
        if (found.isPresent()) { // not use lambda because of throws definition
            final ActionExecute execute = found.get();
            if (needsTrailingSlashRedirect(request, requestPath, execute)) { // index() or by request parameter
                redirectWithTrailingSlash(request, response, contextPath, requestPath);
            } else {
                processAction(request, response, execute, null); // #to_action
            }
            return true;
        } else { // e.g. not found index()
            return false;
        }
    }

    // -----------------------------------------------------
    //                                        Trailing Slash
    //                                        --------------
    protected boolean needsTrailingSlashRedirect(HttpServletRequest request, String requestPath, ActionExecute execute) {
        if (isOutOfTrailingSlashRedirect(request, requestPath, execute)) {
            return false;
        }
        if (isSuppressTrailingSlashRedirect(request, requestPath, execute)) {
            return false;
        }
        return isNonTrailingSlashRequest(request, requestPath, execute);
    }

    protected boolean isOutOfTrailingSlashRedirect(HttpServletRequest request, String requestPath, ActionExecute execute) {
        return execute.isApiExecute(); // API does not need it (SEO handling)
    }

    protected boolean isSuppressTrailingSlashRedirect(HttpServletRequest request, String requestPath, ActionExecute execute) {
        return getActionAdjustmentProvider().isSuppressTrailingSlashRedirect(request, requestPath, execute);
    }

    protected boolean isNonTrailingSlashRequest(HttpServletRequest request, String requestPath, ActionExecute execute) {
        return "GET".equalsIgnoreCase(request.getMethod()) && !requestPath.endsWith("/"); // default determination
    }

    protected void redirectWithTrailingSlash(HttpServletRequest request, HttpServletResponse response, String contextPath,
            String requestPath) throws IOException {
        final String queryString = request.getQueryString();
        final String redirectUrl = contextPath + requestPath + "/" + (queryString != null ? "?" + queryString : "");
        logger.debug("...Redirecting (with trailing slash) to: {}", redirectUrl);
        getRequestManager().getResponseManager().movedPermanently(HtmlResponse.fromRedirectPathAsIs(redirectUrl));
    }

    // ===================================================================================
    //                                                                      Process Action
    //                                                                      ==============
    protected void processAction(HttpServletRequest request, HttpServletResponse response, ActionExecute execute, String paramPath)
            throws IOException, ServletException {
        logger.debug("...Routing to action: name={} params={}", execute.getActionMapping().getActionName(), paramPath);
        LaActionExecuteUtil.setActionExecute(execute); // for e.g. tag-library use
        getRequestProcessor().process(execute, analyzeUrlParam(execute, paramPath)); // #to_action
    }

    // -----------------------------------------------------
    //                                      Request UrlParam
    //                                      ----------------
    protected RequestUrlParam analyzeUrlParam(ActionExecute execute, String paramPath) {
        return getUrlParamAnalyzer().analyzeUrlParam(execute, paramPath);
    }

    // -----------------------------------------------------
    //                                     Request Processor
    //                                     -----------------
    protected ActionRequestProcessor getRequestProcessor() throws ServletException {
        if (lazyLoadedProcessor == null) {
            synchronized (this) {
                prepareRequestProcessorIfNeeds();
            }
        }
        return lazyLoadedProcessor;
    }

    protected void prepareRequestProcessorIfNeeds() throws ServletException {
        if (lazyLoadedProcessor == null) { // re-confirm
            lazyLoadedProcessor = newActionRequestProcessor();
            lazyLoadedProcessor.initialize(LaModuleConfigUtil.getModuleConfig());
        }
    }

    protected ActionRequestProcessor newActionRequestProcessor() {
        return new ActionRequestProcessor();
    }

    // ===================================================================================
    //                                                                             Destroy
    //                                                                             =======
    @Override
    public void destroy() {
    }

    // ===================================================================================
    //                                                                           Component
    //                                                                           =========
    protected FwAssistantDirector getAssistantDirector() {
        if (cachedAssistantDirector != null) {
            return cachedAssistantDirector;
        }
        synchronized (this) {
            if (cachedAssistantDirector != null) {
                return cachedAssistantDirector;
            }
            cachedAssistantDirector = ContainerUtil.getComponent(FwAssistantDirector.class);
        }
        return cachedAssistantDirector;
    }

    protected RequestManager getRequestManager() {
        if (cachedRequestManager != null) {
            return cachedRequestManager;
        }
        synchronized (this) {
            if (cachedRequestManager != null) {
                return cachedRequestManager;
            }
            cachedRequestManager = ContainerUtil.getComponent(RequestManager.class);
        }
        return cachedRequestManager;
    }

    protected ActionAdjustmentProvider getActionAdjustmentProvider() {
        if (cachedActionAdjustmentProvider != null) {
            return cachedActionAdjustmentProvider;
        }
        synchronized (this) {
            if (cachedActionAdjustmentProvider != null) {
                return cachedActionAdjustmentProvider;
            }
            cachedActionAdjustmentProvider = getAssistantDirector().assistWebDirection().assistActionAdjustmentProvider();
        }
        return cachedActionAdjustmentProvider;
    }

    protected RequestUrlParamAnalyzer getUrlParamAnalyzer() {
        if (cachedUrlParamAnalyzer != null) {
            return cachedUrlParamAnalyzer;
        }
        synchronized (this) {
            if (cachedUrlParamAnalyzer != null) {
                return cachedUrlParamAnalyzer;
            }
            cachedUrlParamAnalyzer = new RequestUrlParamAnalyzer(getRequestManager());
        }
        return cachedUrlParamAnalyzer;
    }
}
