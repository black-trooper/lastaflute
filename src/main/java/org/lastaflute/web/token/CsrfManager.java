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
package org.lastaflute.web.token;

import org.lastaflute.web.exception.CrossSiteRequestForgeriesForbiddenException;

/**
 * @author jflute
 * @since 0.4.0 (2015/06/22 Monday)
 */
public interface CsrfManager {

    /**
     * Begin token for CSRF. (e.g. saving the token to request header, session) <br>
     * You should call this when e.g. first access, login, ...
     */
    void beginToken();

    /**
     * Verify token for CSRF. (e.g. checking the token in request header) <br>
     * You can call this in e.g. action hook.
     * @throws CrossSiteRequestForgeriesForbiddenException When the token is invalid or not found.
     */
    void verifyToken();
}
