/**
 * Copyright 2016 Symantec Corporation.
 * 
 * Licensed under the Apache License, Version 2.0 (the “License”); 
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.symcpe.hendrix.api.security;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

/**
 * Custom authorization filter to integrate with internal gateway
 * 
 * @author ambud_sharma
 */
@Priority(1000)
public class BapiAuthorizationFilter implements ContainerRequestFilter {
	
	private static final String BAPI = "bapi";
	public static final String USERNAME = "Username";
	public static final String USER_GROUP = "Group";
	public static final String USER_ROLE = "Role";
	private static final Logger logger = Logger.getLogger(BapiAuthorizationFilter.class.getName());

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		MultivaluedMap<String, String> headers = requestContext.getHeaders();
		if(!headers.containsKey(USERNAME) || !headers.containsKey(USER_ROLE)) {
			requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
			return;
		}
		String username = headers.getFirst(USERNAME);
		final Set<String> roles = new HashSet<>(Arrays.asList(headers.getFirst(USER_ROLE).toLowerCase().split(",")));
		logger.fine("Authenticated request for path:"+requestContext.getUriInfo().getPath()+" from user:"+username+"\troles:"+roles);
		requestContext.setSecurityContext(new BapiSecurityContext(username, roles));
	}
	
	/**
	 * Security context to bridge with dynamic RBAC enforcement by Jersey
	 * 
	 * @author ambud_sharma
	 */
	public static class BapiSecurityContext implements SecurityContext {
		private Set<String> roles;
		private String username;

		protected BapiSecurityContext(String username, final Set<String> roles) {
			this.username = username;
			this.roles = roles;
		}

		@Override
		public Principal getUserPrincipal() {
		    return new UserPrincipal(username);
		}

		@Override
		public boolean isUserInRole(String role) {
		    return roles.contains(role);
		}

		@Override
		public boolean isSecure() {
		    return false;
		}

		@Override
		public String getAuthenticationScheme() {
		    return BAPI;
		}
	}

}