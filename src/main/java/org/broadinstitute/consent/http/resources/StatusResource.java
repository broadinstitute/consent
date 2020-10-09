package org.broadinstitute.consent.http.resources;

import static org.broadinstitute.consent.http.ConsentModule.DB_ENV;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheck.Result;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@Path("status")
public class StatusResource {

  private final HealthCheckRegistry healthChecks;

  public StatusResource(HealthCheckRegistry healthChecks) {
    this.healthChecks = healthChecks;
  }

  @GET
  @Produces("application/json")
  public Response getStatus() {
    Map<String, HealthCheck.Result> results = healthChecks.runHealthChecks();
    StatusCheckResponse response = formatResponse(results);
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    return response.isOk()
        ? Response.ok(gson.toJson(response)).build()
        : Response.serverError().entity(gson.toJson(response)).build();
  }

  private StatusCheckResponse formatResponse(Map<String, HealthCheck.Result> results) {
    List<SubsystemStatus> subsystemStatuses =
        results.entrySet().stream()
            .map(
                entry ->
                    new SubsystemStatus(
                        entry.getKey(), entry.getValue().isHealthy(), entry.getValue()))
            .collect(Collectors.toList());

    // OK status is valid as long as the database is up.
    boolean ok = subsystemStatuses.stream().anyMatch(s -> s.ok && s.name.equals(DB_ENV));
    // If any subsystem is not OK, we consider the system degraded
    boolean degraded = subsystemStatuses.stream().anyMatch(s -> !s.ok);
    Map<String, Result> systems =
        subsystemStatuses.stream()
            .collect(Collectors.toMap(SubsystemStatus::getName, SubsystemStatus::getResult));
    return new StatusCheckResponse(ok, degraded, systems);
  }

  private static class StatusCheckResponse {
    private boolean ok;
    private boolean degraded;
    private Map<String, Result> systems;

    public StatusCheckResponse(boolean ok, boolean degraded, Map<String, Result> systems) {
      this.ok = ok;
      this.degraded = degraded;
      this.systems = systems;
    }

    public boolean isOk() {
      return ok;
    }

    public void setOk(boolean ok) {
      this.ok = ok;
    }

    public boolean isDegraded() {
      return degraded;
    }

    public void setDegraded(boolean degraded) {
      this.degraded = degraded;
    }

    public Map<String, Result> getSystems() {
      return systems;
    }

    public void setSystems(Map<String, Result> systems) {
      this.systems = systems;
    }
  }

  private static class SubsystemStatus {
    String name;
    boolean ok;
    Result result;

    public SubsystemStatus(String name, boolean ok, Result result) {
      this.name = name;
      this.ok = ok;
      this.result = result;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public boolean isOk() {
      return ok;
    }

    public void setOk(boolean ok) {
      this.ok = ok;
    }

    public Result getResult() {
      return result;
    }

    public void setResult(Result result) {
      this.result = result;
    }
  }
}
