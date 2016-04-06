/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.rs.security.oidc.idp;

import java.util.List;
import java.util.logging.Level;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.cxf.rs.security.oauth2.common.Client;
import org.apache.cxf.rs.security.oauth2.common.OAuthError;
import org.apache.cxf.rs.security.oauth2.common.OAuthPermission;
import org.apache.cxf.rs.security.oauth2.common.OAuthRedirectionState;
import org.apache.cxf.rs.security.oauth2.common.ServerAccessToken;
import org.apache.cxf.rs.security.oauth2.common.UserSubject;
import org.apache.cxf.rs.security.oauth2.grants.code.AuthorizationCodeRegistration;
import org.apache.cxf.rs.security.oauth2.provider.OAuthServiceException;
import org.apache.cxf.rs.security.oauth2.services.AuthorizationCodeGrantService;
import org.apache.cxf.rs.security.oauth2.utils.OAuthConstants;
import org.apache.cxf.rs.security.oidc.utils.OidcUtils;

public class OidcAuthorizationCodeService extends AuthorizationCodeGrantService {
    private static final String PROMPT_PARAMETER = "prompt";
    
    private boolean skipAuthorizationWithOidcScope;
    @Override
    protected boolean canAuthorizationBeSkipped(Client client,
                                                UserSubject userSubject,
                                                List<String> requestedScope,
                                                List<OAuthPermission> permissions) {
        // No need to challenge the authenticated user with the authorization form 
        // if all the client application redirecting a user needs is to get this user authenticated
        // with OIDC IDP
        return requestedScope.size() == 1 && permissions.size() == 1 && skipAuthorizationWithOidcScope
            && OidcUtils.OPENID_SCOPE.equals(requestedScope.get(0));
    }
    public void setSkipAuthorizationWithOidcScope(boolean skipAuthorizationWithOidcScope) {
        this.skipAuthorizationWithOidcScope = skipAuthorizationWithOidcScope;
    }
    
    @Override
    protected Response startAuthorization(MultivaluedMap<String, String> params, 
                                          UserSubject userSubject,
                                          Client client) {    
        // Validate the prompt - if it contains "none" then an error is returned with any other value
        String prompt = params.getFirst(PROMPT_PARAMETER);
        if (prompt != null) {
            String[] promptValues = prompt.trim().split(" ");
            if (promptValues.length > 1) {
                for (String promptValue : promptValues) {
                    if ("none".equals(promptValue)) {
                        LOG.log(Level.FINE, "The prompt value {} is invalid", prompt);
                        throw new OAuthServiceException(new OAuthError(OAuthConstants.INVALID_REQUEST));
                    }
                }
            }
        }
        
        return super.startAuthorization(params, userSubject, client);
    }
    
    protected AuthorizationCodeRegistration createCodeRegistration(OAuthRedirectionState state, 
                                                                   Client client, 
                                                                   List<String> requestedScope, 
                                                                   List<String> approvedScope, 
                                                                   UserSubject userSubject, 
                                                                   ServerAccessToken preauthorizedToken) {
        AuthorizationCodeRegistration codeReg = super.createCodeRegistration(state, 
                                                                             client, 
                                                                             requestedScope, 
                                                                             approvedScope, 
                                                                             userSubject, 
                                                                             preauthorizedToken);
        
        codeReg.getExtraProperties().putAll(state.getExtraProperties());
        return codeReg;
    }
    @Override
    protected OAuthRedirectionState recreateRedirectionStateFromParams(
        MultivaluedMap<String, String> params) {
        OAuthRedirectionState state = super.recreateRedirectionStateFromParams(params);
        OidcUtils.setStateClaimsProperty(state, params);
        return state;
    }
}