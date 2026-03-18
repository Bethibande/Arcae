package com.bethibande.repository.web.api;

import com.bethibande.repository.cache.DistributedCacheRegistry;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/api/v1/cache")
@RolesAllowed("SYSTEM")
public class CacheEndpoint {

    @Inject
    protected DistributedCacheRegistry distributedCacheRegistry;

    @DELETE
    @Path("/{cache}/{key}")
    public void delete(final @PathParam("cache") String cache, final @PathParam("key") String key) {
        this.distributedCacheRegistry.invalidateLocal(cache, key);
    }

}
