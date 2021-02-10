package org.broadinstitute.consent.http.db.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.broadinstitute.consent.http.models.ResearcherProperty;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class ResearcherPropertyMapper implements RowMapper<ResearcherProperty> {

  @Override
  public ResearcherProperty map(ResultSet r, StatementContext statementContext)
      throws SQLException {
    return new ResearcherProperty(
        r.getInt("propertyId"),
        r.getInt("userId"),
        r.getString("propertyKey"),
        r.getString("propertyValue"));
  }
}