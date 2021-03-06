package org.broadinstitute.consent.http.db.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.broadinstitute.consent.http.models.AccessRP;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class AccessRPMapper implements RowMapper<AccessRP> {

  public AccessRP map(ResultSet r, StatementContext ctx) throws SQLException {

    return new AccessRP(r.getInt("id"), r.getInt("electionAccessId"), r.getInt("electionRPId"));
  }
}
