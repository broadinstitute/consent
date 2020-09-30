package org.broadinstitute.consent.http.authentication;

import javax.annotation.Priority;
import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.FeatureContext;
import org.glassfish.jersey.server.model.AnnotatedMethod;

/**
 * A copy of org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature that instead returns
 * a NotFoundException instead of a ForbiddenException when a user is not role-authorized for an
 * endpoint.
 */
public class NotFoundRolesAllowedDynamicFeature implements DynamicFeature {

  @Override
  public void configure(final ResourceInfo resourceInfo, final FeatureContext configuration) {
    final AnnotatedMethod am = new AnnotatedMethod(resourceInfo.getResourceMethod());

    // DenyAll on the method take precedence over RolesAllowed and PermitAll
    if (am.isAnnotationPresent(DenyAll.class)) {
      configuration.register(new NotFoundRolesAllowedDynamicFeature.RolesAllowedRequestFilter());
      return;
    }

    // RolesAllowed on the method takes precedence over PermitAll
    RolesAllowed ra = am.getAnnotation(RolesAllowed.class);
    if (ra != null) {
      configuration.register(new NotFoundRolesAllowedDynamicFeature.RolesAllowedRequestFilter(ra.value()));
      return;
    }

    // PermitAll takes precedence over RolesAllowed on the class
    if (am.isAnnotationPresent(PermitAll.class)) {
      // Do nothing.
      return;
    }

    // DenyAll can't be attached to classes

    // RolesAllowed on the class takes precedence over PermitAll
    ra = resourceInfo.getResourceClass().getAnnotation(RolesAllowed.class);
    if (ra != null) {
      configuration.register(new NotFoundRolesAllowedDynamicFeature.RolesAllowedRequestFilter(ra.value()));
    }
  }

  @Priority(Priorities.AUTHORIZATION) // authorization filter - should go after any authentication filters
  private static class RolesAllowedRequestFilter implements ContainerRequestFilter {

    private final boolean denyAll;
    private final String[] rolesAllowed;

    RolesAllowedRequestFilter() {
      this.denyAll = true;
      this.rolesAllowed = null;
    }

    RolesAllowedRequestFilter(final String[] rolesAllowed) {
      this.denyAll = false;
      this.rolesAllowed = (rolesAllowed != null) ? rolesAllowed : new String[] {};
    }

    @Override
    public void filter(final ContainerRequestContext requestContext) {
      if (!denyAll) {
        assert rolesAllowed != null;
        if (rolesAllowed.length > 0 && !isAuthenticated(requestContext)) {
          throw new NotFoundException();
        }

        for (final String role : rolesAllowed) {
          if (requestContext.getSecurityContext().isUserInRole(role)) {
            return;
          }
        }
      }

      throw new NotFoundException();
    }

    private static boolean isAuthenticated(final ContainerRequestContext requestContext) {
      return requestContext.getSecurityContext().getUserPrincipal() != null;
    }
  }

}
