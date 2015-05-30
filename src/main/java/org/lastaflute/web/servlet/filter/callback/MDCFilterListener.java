/*
 * Copyright 2014-2015 the original author or authors.
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
package org.lastaflute.web.servlet.filter.callback;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.servlet.ServletException;

import org.jboss.logging.MDC;

/**
 * @author jflute
 * @since 0.6.0 (2015/05/30 Saturday)
 */
public class MDCFilterListener implements FilterListener {

    protected final Map<String, Supplier<Object>> mdcMap; // not null, empty allowed

    public MDCFilterListener(Map<String, Supplier<Object>> mdcMap) {
        if (mdcMap == null) {
            throw new IllegalArgumentException("The argument 'mdcMap' should not be null.");
        }
        this.mdcMap = mdcMap;
    }

    @Override
    public void listen(FilterListenerChain chain) throws IOException, ServletException {
        final Map<String, Object> originallyMap = createOriginallyMap();
        if (originallyMap != null) {
            mdcMap.forEach((key, value) -> {
                final Object originallyValue = MDC.get(key);
                if (originallyValue != null) {
                    originallyMap.put(key, originallyValue);
                }
            });
        }
        try {
            mdcMap.forEach((key, value) -> MDC.put(key, value));
            chain.doNext();
        } finally {
            if (originallyMap != null) {
                originallyMap.forEach((key, value) -> MDC.remove(key));
            }
        }
    }

    protected Map<String, Object> createOriginallyMap() {
        return !mdcMap.isEmpty() ? new LinkedHashMap<String, Object>() : null;
    }
}
